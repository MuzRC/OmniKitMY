use crate::cancel;
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use std::fs::File;
use std::mem::ManuallyDrop;
use std::os::unix::fs::FileExt;
use std::os::unix::io::FromRawFd;
use std::time::{Duration, Instant};

const SEQ_BLOCK_SIZE: usize = 1024 * 1024; // 1 MiB
const RAND_BLOCK_SIZE: usize = 4096; // 4 KiB
const RAND_WRITE_OPS: u32 = 512;
const RAND_READ_OPS: u32 = 1024;
const PROGRESS_INTERVAL: Duration = Duration::from_millis(200);

#[inline]
fn next_u64(state: &mut u64) -> u64 {
    *state = state.wrapping_add(0x9E37_79B9_7F4A_7C15);
    let mut z = *state;
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

fn fill_non_compressible(buf: &mut [u8], seed: u64) {
    let mut s = seed;
    let mut i = 0;
    while i + 8 <= buf.len() {
        buf[i..i + 8].copy_from_slice(&next_u64(&mut s).to_le_bytes());
        i += 8;
    }
    while i < buf.len() {
        buf[i] = (next_u64(&mut s) & 0xFF) as u8;
        i += 1;
    }
}

fn drop_page_cache(fd: i32) {
    unsafe {
        let _ = libc::posix_fadvise(fd, 0 as libc::off_t, 0 as libc::off_t, libc::POSIX_FADV_DONTNEED);
    }
}

/// Menghantar progres bernormalisasi 0..1000 kepada Kotlin:
/// 0..350   -> Sequential Write
/// 350..650 -> Sequential Read
/// 650..820 -> Random 4K Write
/// 820..1000 -> Random 4K Read
fn push_stage_progress(env: &mut JNIEnv, callback: &JObject, permille: u64, speed_mbps: f64) {
    if callback.is_null() {
        return;
    }
    let _ = env.call_method(
        callback,
        "onProgress",
        "(JJD)V",
        &[
            JValue::Long(permille.min(1000) as i64),
            JValue::Long(1000),
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

pub fn run_speed_benchmark(
    env: &mut JNIEnv,
    fd: i32,
    test_bytes: u64,
    callback: &JObject,
) -> String {
    if fd < 0 || test_bytes < (8 * 1024 * 1024) {
        return r#"{"seqWriteMbps":0.0,"seqWriteMinMbps":0.0,"seqWriteMaxMbps":0.0,"seqReadMbps":0.0,"rand4kWriteMbps":0.0,"rand4kWriteIops":0.0,"rand4kReadMbps":0.0,"rand4kReadIops":0.0,"speedDropDetected":false,"cancelled":false,"error":"FD atau saiz ujian tidak sah (minimum 8 MB)"}"#.to_string();
    }

    let file = ManuallyDrop::new(unsafe { File::from_raw_fd(fd) });
    let total_size = (test_bytes / SEQ_BLOCK_SIZE as u64) * SEQ_BLOCK_SIZE as u64;
    let total_blocks = (total_size / SEQ_BLOCK_SIZE as u64).max(1);

    let mut seq_buf = vec![0u8; SEQ_BLOCK_SIZE];
    fill_non_compressible(&mut seq_buf, 0xA5A5_5A5A_1234_5678);

    // =========================================================================
    // 1. SEQUENTIAL WRITE (dengan pemantauan jatuh kelajuan & fsync)
    // =========================================================================
    let mut seq_write_min = f64::MAX;
    let mut seq_write_max = 0.0f64;
    let mut seg_bytes = 0u64;
    let mut seg_start = Instant::now();
    let seq_w_start = Instant::now();
    let mut last_tick = seq_w_start;

    let mut written: u64 = 0;
    while written < total_size {
        if cancel::is_cancelled() {
            return cancelled_json();
        }

        // Ubah 8 bait pertama setiap blok supaya elak deduplikasi pengawal
        seq_buf[0..8].copy_from_slice(&written.to_le_bytes());

        let mut done_in_blk = 0usize;
        while done_in_blk < SEQ_BLOCK_SIZE {
            match file.write_at(&seq_buf[done_in_blk..], written + done_in_blk as u64) {
                Ok(0) => return error_json("Ruang storan penuh semasa ujian tulis."),
                Ok(n) => done_in_blk += n,
                Err(e) => return error_json(&format!("Ralat Sequential Write: {}", e)),
            }
        }

        written += SEQ_BLOCK_SIZE as u64;
        seg_bytes += SEQ_BLOCK_SIZE as u64;

        // Rekod kelajuan setiap segmen 8 MiB untuk kesan throttling / kad nazak
        if seg_bytes >= 8 * 1024 * 1024 {
            let seg_secs = seg_start.elapsed().as_secs_f64().max(0.001);
            let seg_mbps = (seg_bytes as f64 / (1024.0 * 1024.0)) / seg_secs;
            if seg_mbps < seq_write_min {
                seq_write_min = seg_mbps;
            }
            if seg_mbps > seq_write_max {
                seq_write_max = seg_mbps;
            }
            seg_bytes = 0;
            seg_start = Instant::now();
        }

        let now = Instant::now();
        if now.duration_since(last_tick) >= PROGRESS_INTERVAL {
            let elapsed = now.duration_since(seq_w_start).as_secs_f64().max(0.001);
            let cur_mbps = (written as f64 / (1024.0 * 1024.0)) / elapsed;
            let permille = ((written * 340) / total_size).min(340);
            push_stage_progress(env, callback, permille, cur_mbps);
            last_tick = now;
        }
    }

    if let Err(e) = file.sync_all() {
        return error_json(&format!("Ralat fsync Sequential Write: {}", e));
    }

    let seq_w_secs = seq_w_start.elapsed().as_secs_f64().max(0.001);
    let seq_write_mbps = (total_size as f64 / (1024.0 * 1024.0)) / seq_w_secs;
    if seq_write_min == f64::MAX {
        seq_write_min = seq_write_mbps;
    }
    if seq_write_max == 0.0 {
        seq_write_max = seq_write_mbps;
    }
    let speed_drop_detected = total_blocks >= 32
        && seq_write_max > 5.0
        && (seq_write_min < seq_write_max * 0.40);

    push_stage_progress(env, callback, 350, seq_write_mbps);

    // =========================================================================
    // 2. SEQUENTIAL READ (kosongkan page cache dahulu)
    // =========================================================================
    drop_page_cache(fd);
    let seq_r_start = Instant::now();
    last_tick = seq_r_start;
    let mut read_total: u64 = 0;

    while read_total < total_size {
        if cancel::is_cancelled() {
            return cancelled_json();
        }

        let mut done_in_blk = 0usize;
        while done_in_blk < SEQ_BLOCK_SIZE {
            match file.read_at(&mut seq_buf[done_in_blk..], read_total + done_in_blk as u64) {
                Ok(0) => return error_json("EOF mengejut semasa Sequential Read."),
                Ok(n) => done_in_blk += n,
                Err(e) => return error_json(&format!("Ralat Sequential Read: {}", e)),
            }
        }
        read_total += SEQ_BLOCK_SIZE as u64;

        let now = Instant::now();
        if now.duration_since(last_tick) >= PROGRESS_INTERVAL {
            let elapsed = now.duration_since(seq_r_start).as_secs_f64().max(0.001);
            let cur_mbps = (read_total as f64 / (1024.0 * 1024.0)) / elapsed;
            let permille = 350 + ((read_total * 300) / total_size).min(300);
            push_stage_progress(env, callback, permille, cur_mbps);
            last_tick = now;
        }
    }

    let seq_r_secs = seq_r_start.elapsed().as_secs_f64().max(0.001);
    let seq_read_mbps = (total_size as f64 / (1024.0 * 1024.0)) / seq_r_secs;
    push_stage_progress(env, callback, 650, seq_read_mbps);

    // =========================================================================
    // 3. RANDOM 4K WRITE (sejajar 4096 bait + fsync berkala & akhir)
    // =========================================================================
    let total_4k_slots = (total_size / RAND_BLOCK_SIZE as u64).max(1);
    let mut rand_buf = vec![0u8; RAND_BLOCK_SIZE];
    fill_non_compressible(&mut rand_buf, 0xCAFE_BABE_9988_7766);

    let mut rng_state = 0x1357_9BDF_2468_ACE0u64;
    let rand_w_start = Instant::now();
    last_tick = rand_w_start;

    for op in 0..RAND_WRITE_OPS {
        if cancel::is_cancelled() {
            return cancelled_json();
        }
        let slot = next_u64(&mut rng_state) % total_4k_slots;
        let off = slot * RAND_BLOCK_SIZE as u64;
        rand_buf[0..4].copy_from_slice(&op.to_le_bytes());

        if let Err(e) = file.write_at(&rand_buf, off) {
            return error_json(&format!("Ralat Random 4K Write: {}", e));
        }

        // Sync setiap 64 operasi supaya mencerminkan kependaman cip NAND sebenar
        if (op + 1) % 64 == 0 {
            if let Err(e) = file.sync_data() {
                return error_json(&format!("Ralat sync Random 4K Write: {}", e));
            }
        }

        let now = Instant::now();
        if now.duration_since(last_tick) >= PROGRESS_INTERVAL {
            let done_ops = (op + 1) as u64;
            let elapsed = now.duration_since(rand_w_start).as_secs_f64().max(0.001);
            let cur_mbps = ((done_ops * RAND_BLOCK_SIZE as u64) as f64 / (1024.0 * 1024.0)) / elapsed;
            let permille = 650 + ((done_ops * 170) / RAND_WRITE_OPS as u64).min(170);
            push_stage_progress(env, callback, permille, cur_mbps);
            last_tick = now;
        }
    }

    if let Err(e) = file.sync_all() {
        return error_json(&format!("Ralat fsync akhir Random 4K Write: {}", e));
    }

    let rand_w_secs = rand_w_start.elapsed().as_secs_f64().max(0.001);
    let rand_w_bytes = RAND_WRITE_OPS as f64 * RAND_BLOCK_SIZE as f64;
    let rand4k_write_mbps = (rand_w_bytes / (1024.0 * 1024.0)) / rand_w_secs;
    let rand4k_write_iops = RAND_WRITE_OPS as f64 / rand_w_secs;
    push_stage_progress(env, callback, 820, rand4k_write_mbps);

    // =========================================================================
    // 4. RANDOM 4K READ (kosongkan page cache dahulu)
    // =========================================================================
    drop_page_cache(fd);
    let rand_r_start = Instant::now();
    last_tick = rand_r_start;

    for op in 0..RAND_READ_OPS {
        if cancel::is_cancelled() {
            return cancelled_json();
        }
        let slot = next_u64(&mut rng_state) % total_4k_slots;
        let off = slot * RAND_BLOCK_SIZE as u64;

        if let Err(e) = file.read_at(&mut rand_buf, off) {
            return error_json(&format!("Ralat Random 4K Read: {}", e));
        }

        let now = Instant::now();
        if now.duration_since(last_tick) >= PROGRESS_INTERVAL {
            let done_ops = (op + 1) as u64;
            let elapsed = now.duration_since(rand_r_start).as_secs_f64().max(0.001);
            let cur_mbps = ((done_ops * RAND_BLOCK_SIZE as u64) as f64 / (1024.0 * 1024.0)) / elapsed;
            let permille = 820 + ((done_ops * 180) / RAND_READ_OPS as u64).min(180);
            push_stage_progress(env, callback, permille, cur_mbps);
            last_tick = now;
        }
    }

    let rand_r_secs = rand_r_start.elapsed().as_secs_f64().max(0.001);
    let rand_r_bytes = RAND_READ_OPS as f64 * RAND_BLOCK_SIZE as f64;
    let rand4k_read_mbps = (rand_r_bytes / (1024.0 * 1024.0)) / rand_r_secs;
    let rand4k_read_iops = RAND_READ_OPS as f64 / rand_r_secs;
    push_stage_progress(env, callback, 1000, rand4k_read_mbps);

    format!(
        r#"{{"seqWriteMbps":{:.2},"seqWriteMinMbps":{:.2},"seqWriteMaxMbps":{:.2},"seqReadMbps":{:.2},"rand4kWriteMbps":{:.2},"rand4kWriteIops":{:.1},"rand4kReadMbps":{:.2},"rand4kReadIops":{:.1},"speedDropDetected":{},"cancelled":false,"error":""}}"#,
        seq_write_mbps,
        seq_write_min,
        seq_write_max,
        seq_read_mbps,
        rand4k_write_mbps,
        rand4k_write_iops,
        rand4k_read_mbps,
        rand4k_read_iops,
        speed_drop_detected
    )
}

fn cancelled_json() -> String {
    r#"{"seqWriteMbps":0.0,"seqWriteMinMbps":0.0,"seqWriteMaxMbps":0.0,"seqReadMbps":0.0,"rand4kWriteMbps":0.0,"rand4kWriteIops":0.0,"rand4kReadMbps":0.0,"rand4kReadIops":0.0,"speedDropDetected":false,"cancelled":true,"error":""}"#.to_string()
}

fn error_json(msg: &str) -> String {
    format!(
        r#"{{"seqWriteMbps":0.0,"seqWriteMinMbps":0.0,"seqWriteMaxMbps":0.0,"seqReadMbps":0.0,"rand4kWriteMbps":0.0,"rand4kWriteIops":0.0,"rand4kReadMbps":0.0,"rand4kReadIops":0.0,"speedDropDetected":false,"cancelled":false,"error":"{}"}}"#,
        escape_json(msg)
    )
}
