#ifndef TIMEBOX_MINIAUDIO_H
#define TIMEBOX_MINIAUDIO_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*tb_audio_render_proc)(void* pUserData, float* pFrames, unsigned int frameCount);

int tb_audio_device_init(
    void** ppDevice,
    unsigned int sampleRate,
    unsigned int periodFrames,
    unsigned int periodCount,
    tb_audio_render_proc renderProc,
    void* pUserData
);

int tb_audio_device_start(void* pDevice);
int tb_audio_device_stop(void* pDevice);
void tb_audio_device_uninit(void* pDevice);

#ifdef __cplusplus
}
#endif

#endif
