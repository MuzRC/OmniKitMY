mod audit;
mod benchmark;
mod cancel;
mod verify;
mod wipe;

use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeEngineInfo(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let arch = std::env::consts::ARCH;
    let info = format!("Rust Core v{} ({}, 16KB-ready)", env!("CARGO_PKG_VERSION"), arch);
    match env.new_string(info) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeRequestCancel(
    _env: JNIEnv,
    _class: JClass,
) {
    cancel::request_cancel();
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeResetCancel(
    _env: JNIEnv,
    _class: JClass,
) {
    cancel::reset_cancel();
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeIsCancelled(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if cancel::is_cancelled() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeWriteVerifyFile(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    file_index: jint,
    target_bytes: jlong,
    session_seed: jlong,
    callback: JObject,
) -> jstring {
    let json = verify::write_verify_file(
        &mut env,
        fd as i32,
        file_index as u32,
        target_bytes.max(0) as u64,
        session_seed as u64,
        &callback,
    );
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeVerifyFile(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    file_index: jint,
    expected_bytes: jlong,
    session_seed: jlong,
    callback: JObject,
) -> jstring {
    let json = verify::verify_file(
        &mut env,
        fd as i32,
        file_index as u32,
        expected_bytes.max(0) as u64,
        session_seed as u64,
        &callback,
    );
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeRunSpeedBenchmark(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    test_bytes: jlong,
    callback: JObject,
) -> jstring {
    let json = benchmark::run_speed_benchmark(
        &mut env,
        fd as i32,
        test_bytes.max(0) as u64,
        &callback,
    );
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeHashFdBlake3(
    env: JNIEnv,
    _class: JClass,
    fd: jint,
    file_size: jlong,
    quick_only: jboolean,
) -> jstring {
    let json = audit::hash_fd_blake3(fd as i32, file_size.max(0) as u64, quick_only == JNI_TRUE);
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeBatchHashPathsBlake3<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    paths_array: JObjectArray<'local>,
    quick_only: jboolean,
) -> jstring {
    let paths = extract_string_array(&mut env, &paths_array);
    let json = audit::batch_hash_paths_blake3(&paths, quick_only == JNI_TRUE);
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeInspectMediaFd<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    fd: jint,
    file_size: jlong,
    ext: JString<'local>,
) -> jstring {
    let ext_str = env
        .get_string(&ext)
        .map(String::from)
        .unwrap_or_default();
    let json = audit::inspect_media_fd(fd as i32, file_size.max(0) as u64, &ext_str);
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeBatchInspectMediaPaths<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    paths_array: JObjectArray<'local>,
) -> jstring {
    let paths = extract_string_array(&mut env, &paths_array);
    let json = audit::batch_inspect_media_paths(&paths);
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muz_sdprobe_core_nativebridge_NativeBridge_nativeZeroFillFile(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    target_bytes: jlong,
    callback: JObject,
) -> jstring {
    let json = wipe::zero_fill_file(&mut env, fd as i32, target_bytes.max(0) as u64, &callback);
    match env.new_string(json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn extract_string_array<'local>(
    env: &mut JNIEnv<'local>,
    array: &JObjectArray<'local>,
) -> Vec<String> {
    let len = env.get_array_length(array).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut out = Vec::with_capacity(len as usize);
    for i in 0..len {
        let item = match env.get_object_array_element(array, i) {
            Ok(obj) if !obj.is_null() => {
                let jstr = JString::from(obj);
                env.get_string(&jstr)
                    .map(String::from)
                    .unwrap_or_default()
            }
            _ => String::new(),
        };
        out.push(item);
    }
    out
}
