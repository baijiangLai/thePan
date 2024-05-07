package com.thepan.utils;

import com.thepan.constants.Constants;
import com.thepan.entity.dao.SessionWebUserDto;

import javax.servlet.http.HttpSession;

public class SessionUtil {
    public static SessionWebUserDto getUserInfoFromSession(HttpSession session) {
        return (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
    }
}
