#define MA_ENABLE_ONLY_SPECIFIC_BACKENDS
#define MA_ENABLE_WASAPI
#define MA_NO_DECODING
#define MA_NO_ENCODING
#define MA_NO_WAV
#define MA_NO_FLAC
#define MA_NO_MP3
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_ENGINE
#define MA_NO_GENERATION
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
#include "timebox_miniaudio.h"

#include <stdlib.h>

typedef struct tb_audio_device {
    ma_device device;
    tb_audio_render_proc renderProc;
    void* pUserData;
} tb_audio_device;

static void tb_data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount)
{
    tb_audio_device* self;
    (void)pInput;
    if (pDevice == NULL || pOutput == NULL || frameCount == 0) {
        return;
    }
    self = (tb_audio_device*)pDevice->pUserData;
    if (self == NULL || self->renderProc == NULL) {
        return;
    }
    self->renderProc(self->pUserData, (float*)pOutput, (unsigned int)frameCount);
}

int tb_audio_device_init(
    void** ppDevice,
    unsigned int sampleRate,
    unsigned int periodFrames,
    unsigned int periodCount,
    tb_audio_render_proc renderProc,
    void* pUserData
) {
    tb_audio_device* self;
    ma_device_config config;
    ma_result result;

    if (ppDevice == NULL || renderProc == NULL || sampleRate == 0 || periodFrames == 0 || periodCount == 0) {
        return -1;
    }
    *ppDevice = NULL;

    self = (tb_audio_device*)calloc(1, sizeof(*self));
    if (self == NULL) {
        return -1;
    }
    self->renderProc = renderProc;
    self->pUserData = pUserData;

    config = ma_device_config_init(ma_device_type_playback);
    config.playback.format = ma_format_f32;
    config.playback.channels = 1;
    config.sampleRate = sampleRate;
    config.periodSizeInFrames = periodFrames;
    config.periods = periodCount;
    config.performanceProfile = ma_performance_profile_conservative;
    config.noFixedSizedCallback = MA_TRUE;
    config.dataCallback = tb_data_callback;
    config.pUserData = self;

    result = ma_device_init(NULL, &config, &self->device);
    if (result != MA_SUCCESS) {
        free(self);
        return -1;
    }
    *ppDevice = self;
    return 0;
}

int tb_audio_device_start(void* pDevice)
{
    tb_audio_device* self = (tb_audio_device*)pDevice;
    if (self == NULL) {
        return -1;
    }
    if (ma_device_start(&self->device) != MA_SUCCESS) {
        return -1;
    }
    return 0;
}

int tb_audio_device_stop(void* pDevice)
{
    tb_audio_device* self = (tb_audio_device*)pDevice;
    if (self == NULL) {
        return -1;
    }
    if (ma_device_stop(&self->device) != MA_SUCCESS) {
        return -1;
    }
    return 0;
}

void tb_audio_device_uninit(void* pDevice)
{
    tb_audio_device* self = (tb_audio_device*)pDevice;
    if (self == NULL) {
        return;
    }
    ma_device_uninit(&self->device);
    free(self);
}
