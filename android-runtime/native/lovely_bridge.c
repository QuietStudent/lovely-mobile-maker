/* LMM mobile bridge: load a versioned Android Lovely library once per process. */
#include <dlfcn.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
#include "lovely.h"

static pthread_once_t lmm_once = PTHREAD_ONCE_INIT;
static void (*lmm_init)(luaL_loadbufferx_ptr, struct LuaLib);
static int (*lmm_apply)(lua_State *, const char *, size_t, const char *, const char *);
static char lmm_error[512];

static void lmm_open(void)
{
  const char *path = getenv("LMM_LOVELY_LIBRARY");
  if (!path || !*path) {
    snprintf(lmm_error, sizeof(lmm_error), "Select a Lovely version in the LMM launcher first.");
    return;
  }
  /* Keep this handle alive: Lovely stores callbacks and process-wide state. */
  void *handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
  if (!handle) {
    snprintf(lmm_error, sizeof(lmm_error), "Could not load Android Lovely: %s", dlerror());
    return;
  }
  int (*abi)(void) = (int (*)(void)) dlsym(handle, "lovely_mobile_abi");
  if (!abi || abi() != 1) {
    snprintf(lmm_error, sizeof(lmm_error), "This Lovely library does not support LMM mobile API 1.");
    return;
  }
  lmm_init = (void (*)(luaL_loadbufferx_ptr, struct LuaLib)) dlsym(handle, "lovely_init");
  lmm_apply = (int (*)(lua_State *, const char *, size_t, const char *, const char *)) dlsym(handle, "lovely_apply_patches");
  if (!lmm_init || !lmm_apply) {
    snprintf(lmm_error, sizeof(lmm_error), "Android Lovely is missing its patch entry points.");
    lmm_init = NULL;
    lmm_apply = NULL;
  }
}

void lovely_init(luaL_loadbufferx_ptr raw, struct LuaLib lua)
{
  pthread_once(&lmm_once, lmm_open);
  if (lmm_init) lmm_init(raw, lua);
}

int lovely_apply_patches(lua_State *L, const char *buff, size_t sz, const char *name, const char *mode)
{
  if (lmm_apply) return lmm_apply(L, buff, sz, name, mode);
  lua_pushstring(L, lmm_error);
  return LUA_ERRSYNTAX;
}
