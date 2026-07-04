package com.niro.web.event;

import lombok.Getter;

@Getter
public class UserPlatformSettingsChangedEvent {

    private final Long userId;

    public UserPlatformSettingsChangedEvent(Long userId) {
        this.userId = userId;
    }
}
