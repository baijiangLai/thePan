package com.thepan.entity.query;


import lombok.Data;

/**
 * 
 * 用户信息参数
 * 
 */
@Data
public class UserInfoQuery extends BaseParam {


	/**
	 * 用户ID
	 */
	private String userId;

	private String userIdFuzzy;

	/**
	 * 昵称
	 */
	private String nickName;

	private String nickNameFuzzy;

	/**
	 * 邮箱
	 */
	private String email;

	private String emailFuzzy;

	/**
	 * 
	 */
	private String qqAvatar;

	private String qqAvatarFuzzy;

	/**
	 * qqOpenId
	 */
	private String qqOpenId;

	private String qqOpenIdFuzzy;

	/**
	 * 密码
	 */
	private String password;

	private String passwordFuzzy;

	/**
	 * 加入时间
	 */
	private String joinTime;

	private String joinTimeStart;

	private String joinTimeEnd;

	/**
	 * 最后登录时间
	 */
	private String lastLoginTime;

	private String lastLoginTimeStart;

	private String lastLoginTimeEnd;

	/**
	 * 0:禁用 1:正常
	 */
	private Integer status;

	/**
	 * 使用空间大小
	 */
	private Long useSpace;

	/**
	 * 总大小
	 */
	private Long totalSpace;
}
