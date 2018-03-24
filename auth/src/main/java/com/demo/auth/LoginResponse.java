package com.demo.auth;

import java.util.concurrent.CountDownLatch;

/**
 * 登录信息承载类
 * 
 * @author 刘冬博客http://www.cnblogs.com/GoodHelper
 *
 */
public class LoginResponse {

	public CountDownLatch latch;

	public String user;

	// 省略 get set
}
