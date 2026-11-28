use crate::cancel;
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use std::fs::File;
use std::mem::ManuallyDrop;
use std::os::unix::fs::FileExt;
use std::os::unix::io::FromRawFd;
use std::time::{Duration, Instant};

const CHUNK_SIZE: usize = 1024 * 1024; // 1 MiB per blok ujian
const MAGIC_HEADER: u32 = 0x5344_5031; // "SDP1"
const PROGRESS_INTERVAL: Duration = Duration::from_millis(200);

#[inline]
fn splitmix64(state: &mut u64) -> u64 {
    *state = state.wrapping_add(0x9E37_79B9_7F4A_7C15);
    let mut z = *state;
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

fn crc32_ieee(data: &[u8]) -> u32 {
    let mut crc: u32 = 0xFFFF_FFFF;
    for &b in data {
        crc ^= b as u32;
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg();
            crc = (crc >> 1) ^ (0xEDB8_8320 & mask);
        }
    }
    !crc
}

/// Menjana corak data anti-mampatan yang unik untuk setiap (session_seed, file_index, chunk_index).
/// Susun atur blok (>= 24 bait):
/// [0..4]   MAGIC_HEADER
/// [4..12]  session_seed (u64 LE)
/// [12..16] file_index (u32 LE)
/// [16..20] chunk_index (u32 LE)
/// [20..n-4] Aliran PRNG SplitMix64
/// [n-4..n]  CRC32 IEEE bagi [0..n-4]
fn fill_pattern_block(buf: &mut [u8], session_seed: u64, file_index: u32, chunk_index: u32) {
    let len = buf.len();
    if len < 24 {
        let mut s = session_seed ^ ((file_index as u64) << 32) ^ (chunk_index as u64);
        for b in buf.iter_mut() {
            *b = (splitmix64(&mut s) & 0xFF) as u8;
        }
        return;
    }

    buf[0..4].copy_from_slice(&MAGIC_HEADER.to_le_bytes());
    buf[4..12].copy_from_slice(&session_seed.to_le_bytes());
    buf[12..16].copy_from_slice(&file_index.to_le_bytes());
    buf[16..20].copy_from_slice(&chunk_index.to_le_bytes());

    let mut prng_state = session_seed
        .wrapping_mul(0x9E37_79B9_7F4A_7C15)
        ^ ((file_index as u64) << 32)
        ^ ((chunk_index as u64).wrapping_mul(0xD6E8_FEB8_6659_FD93));

    let body_end = len - 4;
    let mut i = 20;
    while i + 8 <= body_end {
        let v = splitmix64(&mut prng_state).to_le_bytes();
        buf[i..i + 8].copy_from_slice(&v);
        i += 8;
    }
    if i < body_end {
        let v = splitmix64(&mut prng_state).to_le_bytes();
        let rem = body_end - i;
        buf[i..body_end].copy_from_slice(&v[..rem]);
    }

    let crc = crc32_ieee(&buf[..body_end]);
    buf[body_end..len].copy_from_slice(&crc.to_le_bytes());
}

fn is_uniform_black_hole(buf: &[u8]) -> bool {
    if buf.is_empty() {
        return true;
    }
    let first = buf[0];
    if first != 0x00 && first != 0xFF {
        return false;
    }
    buf.iter().all(|&b| b == first)
}

fn push_progress(env: &mut JNIEnv, callback: &JObject, done: u64, total: u64, speed_mbps: f64) {
    if callback.is_null() {
        return;
    }
    let _ = env.call_method(
        callback,
        "onProgress",
        "(JJD)V",
        &[
            JValue::Long(done as i64),
            JValue::Long(total as i64),
            JValue::Double(speed_mbps),
        ],
    );
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn escape_json(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for ch in s.chars() {
        match ch {
            '\\' => out.push_str("\\\\"),
            '"' => out.push_str("\\\""),
            '\n' => out.push(' '),
            '\r' => out.push(' '),
            '\t' => out.push(' '),
            c => out.push(c),
        }
    }
    out
}

/// Menulis satu fail ujian (maks 512 MiB) dengan corak anti-mampatan + fsync().
/// Meminjam `fd` melalui ManuallyDrop supaya tidak berlaku double-close.
pub fn write_verify_file(
    env: &mut JNIEnv,
    fd: i32,
    file_index: u32,
    target_bytes: u64,
    session_seed: u64,
    callback: &JObject,
) -> String {
    if fd < 0 || target_bytes == 0 {
        return r#"{"bytesWritten":0,"hitEnospc":false,"cancelled":false,"avgSpeedMbps":0.0,"error":"FD atau saiz sasaran tidak sah"}"#.to_string();
    }

    let file = ManuallyDrop::new(unsafe { File::from_raw_fd(fd) });
    let mut buf = vec![0u8; CHUNK_SIZE];
    let mut offset: u64 = 0;
    let mut chunk_idx: u32 = 0;
    let mut hit_enospc = false;
    let mut cancelled = false;
    let mut error_msg = String::new();

    let start_time = Instant::now();
    let mut last_tick = start_time;

    while offset < target_bytes {
        if cancel::is_cancelled() {
            cancelled = true;
            break;
        }

        let remaining = target_bytes - offset;
        let to_write = remaining.min(CHUNK_SIZE as u64) as usize;
        let slice = &mut buf[..to_write];
        fill_pattern_block(slice, session_seed, file_index, chunk_idx);

        let mut chunk_written = 0usize;
        while chunk_written < to_write {
            match file.write_at(&slice[chunk_written..], offset + chunk_written as u64) {
                Ok(0) => {
                    hit_enospc = true;
                    break;
                }
                Ok(n) => {
                    chunk_written += n;
                }
                Err(e) => {
                    if e.raw_os_error() == Some(libc::ENOSPC) {
                        hit_enospc = true;
                    } else {
                        error_msg = format!("Ralat tulis pada offset {}: {}", offset, e);
                    }
                    break;
                }
            }
        }

        offset += chunk_written as u64;
        chunk_idx = chunk_idx.wrapping_add(1);

        if hit_enospc || !error_msg.is_empty() {
            break;
        }

        let now = Instant::now();
        if now.duration_since(last_tick) >= PROGRESS_INTERVAL {
            let elapsed = now.duration_since(start_time).as_secs_f64().max(0.001);
            let mbps = (offset as f64 / (1024.0 * 1024.0)) / elapsed;
            push_progress(env, callback, offset, target_bytes, mbps);
            last_tick = now;
        }
    }

    // Wajib paksa masuk ke cip storan fizikal (elak RAM write cache)
    if offset > 0 && !cancelled {
        if let Err(e) = file.sync_all() {
            if e.raw_os_error() == Some(libc::ENOSPC) {
                hit_enospc = true;
            } else if error_msg.is_empty() {
                error_msg = format!("Ralat fsync: {}", e);
            }
        }
    }

    let total_secs = start_time.elapsed().as_secs_f64().max(0.001);
    let avg_mbps = (offset as f64 / (1024.0 * 1024.0)) / total_secs;
    push_progress(env, callback, offset, target_bytes, avg_mbps);

    format!(
        r#"{{"bytesWritten":{},"hitEnospc":{},"cancelled":{},"avgSpeedMbps":{:.2},"error":"{}"}}"#,
        offset,
        hit_enospc,
        cancelled,
        avg_mbps,
        escape_json(&error_msg)
    )
}

/// Membaca balik dan mengesahkan setiap blok terhadap corak PRNG + CRC32 asal.
/// Mengelaskan ralat kepada Looping (tindih data lama), Black Hole (0x00/0xFF), atau Corrupt (Bad Block).
pub fn verify_file(
    env: &mut JNIEnv,
    fd: i32,
    file_index: u32,
    expected_bytes: u64,
    session_seed: u64,
    callback: &JObject,
) -> String {
    if fd < 0 || expected_bytes == 0 {
        return r#"{"bytesVerified":0,"validBytes":0,"corruptBlocks":0,"loopingBlocks":0,"blackHoleBlocks":0,"firstErrorOffset":-1,"cancelled":false,"avgSpeedMbps":0.0,"error":"FD atau saiz semakan tidak sah"}"#.to_string();
    }

    // Minta kernel kosongkan page cache untuk fail ini sebelum membaca
    unsafe {
        let _ = libc::posix_fadvise(fd, 0 as libc::off_t, 0 as libc::off_t, libc::POSIX_FADV_DONTNEED);
    }

    let file = ManuallyDrop::new(unsafe { File::from_raw_fd(fd) });
    let mut read_buf = vec![0u8; CHUNK_SIZE];
    let mut expected_buf = vec![0u8; CHUNK_SIZE];

    let mut offset: u64 = 0;
    let mut chunk_idx: u32 = 0;
    let mut valid_bytes: u64 = 0;
    let mut corrupt_blocks: u64 = 0;
    let mut looping_blocks: u64 = 0;
    let mut black_hole_blocks: u64 = 0;
    let mut first_error_offset: i64 = -1;
    let mut cancelled = false;
    let mut error_msg = String::new();

    let start_time = Instant::now();
    let mut last_tick = start_time;

    while offset < expected_bytes {
        if cancel::is_cancelled() {
            cancelled = true;
            break;
        }

        let remaining = expected_bytes - offset;
        let to_read = remaining.min(CHUNK_SIZE as u64) as usize;
        let actual_slice = &mut read_buf[..to_read];

        let mut chunk_read = 0usize;
        let mut eof_early = false;
        while chunk_read < to_read {
            match file.read_at(&mut actual_slice[chunk_read..], offset + chunk_read as u64) {
                Ok(0) => {
                    eof_early = true;
                    break;
                }
                Ok(n) => {
                    chunk_read += n;
                }
                Err(e) => {
                    error_msg = format!("Ralat baca pada offset {}: {}", offset, e);
                    break;
                }
            }
        }

        if !error_msg.is_empty() {
            break;
        }

        if eof_early || chunk_read < to_read {
            black_hole_blocks += 1;
            if first_error_offset < 0 {
                first_error_offset = offset as i64;
            }
            offset += chunk_read as u64;
            break;
        }

        let exp_slice = &mut expected_buf[..to_read];
        fill_pattern_block(exp_slice, session_seed, file_index, chunk_idx);

        if actual_slice == exp_slice {
            valid_bytes += to_read as u64;
        } else {
            if first_error_offset < 0 {
                first_error_offset = offset as i64;
            }
            if is_uniform_black_hole(actual_slice) {
                black_hole_blocks += 1;
            } else if to_read >= 20 {
                let got_magic = u32::from_le_bytes([
                    actual_slice[0],
                    actual_slice[1],
                    actual_slice[2],
                    actual_slice[3],
                ]);
                let got_seed = u64::from_le_bytes([
                    actual_slice[4],
                    actual_slice[5],
                    actual_slice[6],
                    actual_slice[7],
                    actual_slice[8],
                    actual_slice[9],
                    actual_slice[10],
                    actual_slice[11],
                ]);
                let got_file_idx = u32::from_le_bytes([
                    actual_slice[12],
                    actual_slice[13],
                    actual_slice[14],
                    actual_slice[15],
                ]);
                let got_chunk_idx = u32::from_le_bytes([
                    actual_slice[16],
                    actual_slice[17],
                    actual_slice[18],
                    actual_slice[19],
                ]);

                if got_magic == MAGIC_HEADER
                    && got_seed == session_seed
                    && (got_file_idx != file_index || got_chunk_idx != chunk_idx)
                {
                    looping_blocks += 1;
                } else {
                    corrupt_blocks += 1;
                }
            } else {
                corrupt_blocks += 1;
            }
        }

        offset += to_read as u64;
        chunk_idx = chunk_idx.wrapping_add(1);

        let now = Instant::now();
        if now.duration_since(last_tick) >= PROGRESS_INTERVAL {
            let elapsed = now.duration_since(start_time).as_secs_f64().max(0.001);
            let mbps = (offset as f64 / (1024.0 * 1024.0)) / elapsed;
            push_progress(env, callback, offset, expected_bytes, mbps);
            last_tick = now;
        }
    }

    let total_secs = start_time.elapsed().as_secs_f64().max(0.001);
    let avg_mbps = (offset as f64 / (1024.0 * 1024.0)) / total_secs;
    push_progress(env, callback, offset, expected_bytes, avg_mbps);

    format!(
        r#"{{"bytesVerified":{},"validBytes":{},"corruptBlocks":{},"loopingBlocks":{},"blackHoleBlocks":{},"firstErrorOffset":{},"cancelled":{},"avgSpeedMbps":{:.2},"error":"{}"}}"#,
        offset,
        valid_bytes,
        corrupt_blocks,
        looping_blocks,
        black_hole_blocks,
        first_error_offset,
        cancelled,
        avg_mbps,
        escape_json(&error_msg)
    )
}
