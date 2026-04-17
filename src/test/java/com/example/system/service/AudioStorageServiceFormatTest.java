package com.example.system.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioStorageServiceFormatTest {

    @Test
    void allowedExtensions() {
        assertThat(AudioStorageService.isAllowedAudioUpload("mp3", null)).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("mp3 ", null)).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("WAV", null)).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("ogg", null)).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("m4a", null)).isFalse();
        assertThat(AudioStorageService.isAllowedAudioUpload("flac", null)).isFalse();
    }

    @Test
    void allowedMimeWhenExtensionMissing() {
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/mpeg")).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/mpg")).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/x-mp3")).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/ogg")).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/ogg; codecs=opus")).isTrue();
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/mp4")).isFalse();
        assertThat(AudioStorageService.isAllowedAudioUpload("", "audio/webm")).isFalse();
    }
}
