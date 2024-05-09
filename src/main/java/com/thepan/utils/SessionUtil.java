package com.thepan.utils;

import com.thepan.constants.Constants;
import com.thepan.entity.dto.SessionShareDto;
import com.thepan.entity.dto.SessionWebUserDto;

import javax.servlet.http.HttpSession;

public class SessionUtil {
    public static SessionWebUserDto getUserInfoFromSession(HttpSession session) {
        return (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
    }

    public static SessionShareDto getSessionShareFromSession(HttpSession session, String shareId) {
        SessionShareDto sessionShareDto = (SessionShareDto) session.getAttribute(Constants.SESSION_SHARE_KEY + shareId);
        return sessionShareDto;
    }
}
