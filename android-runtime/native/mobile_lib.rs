// Mobile API 1 uses the LuaLib layout from Lovely 0.9.0.
use lovely_core::{Lovely, sys::{LuaLib, LuaState}};
use once_cell::sync::OnceCell;

type LoadBufferX = unsafe extern "C" fn(*mut LuaState, *const u8, usize, *const u8, *const u8) -> u32;
static RUNTIME: OnceCell<&Lovely> = OnceCell::new();
static RECALL: OnceCell<LoadBufferX> = OnceCell::new();

#[no_mangle]
pub extern "C" fn lovely_mobile_abi() -> i32 { 1 }

#[no_mangle]
pub unsafe extern "C" fn lovely_init(raw: LoadBufferX, lua: LuaLib) {
    RUNTIME.get_or_init(|| {
        RECALL.set(raw).expect("Lovely callback already set");
        Lovely::init(&|a,b,c,d,e| RECALL.get_unchecked()(a,b,c,d,e), lua, false)
    });
}

#[no_mangle]
pub unsafe extern "C" fn lovely_apply_patches(state: *mut LuaState, buf: *const u8,
    size: usize, name: *const u8, mode: *const u8) -> u32 {
    RUNTIME.get().expect("Lovely is not initialized").apply_buffer_patches(state, buf, size, name, mode)
}
