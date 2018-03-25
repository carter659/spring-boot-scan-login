package com.demo.auth;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * 控制器
 * 
 * @author 刘冬博客http://www.cnblogs.com/GoodHelper
 *
 */
@Controller
public class MainController {

	private static final String LOGIN_KEY = "key.value.login.";

	@Autowired
	private StringRedisTemplate redisTemplate;

	@GetMapping({ "/", "index" })
	public String index(Model model, @SessionAttribute(WebSecurityConfig.SESSION_KEY) String user) {
		model.addAttribute("user", user);
		return "index";
	}

	@GetMapping("login")
	public String login() {
		return "login";
	}

	/**
	 * 获取二维码
	 * 
	 * @return
	 */
	@GetMapping("login/getQrCode")
	public @ResponseBody Map<String, Object> getQrCode() throws Exception {
		Map<String, Object> result = new HashMap<>();

		String loginId = UUID.randomUUID().toString();
		result.put("loginId", loginId);

		// app端登录地址
		String loginUrl = "http://localhost:8080/login/setUser/loginId/";
		result.put("loginUrl", loginUrl);
		result.put("image", createQrCode(loginUrl));

		ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
		opsForValue.set(LOGIN_KEY + loginId, loginId, 5, TimeUnit.MINUTES);
		return result;
	}

	/**
	 * app二维码登录地址，这里为了测试才传{user},实际项目中user是通过其他方式传值
	 * 
	 * @param loginId
	 * @param user
	 * @return
	 */
	@GetMapping("login/setUser/{loginId}/{user}")
	public @ResponseBody Map<String, Object> setUser(@PathVariable String loginId, @PathVariable String user) {

		ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
		String value = opsForValue.get(LOGIN_KEY + loginId);

		if (value != null) {
			opsForValue.set(LOGIN_KEY + loginId, user, 1, TimeUnit.MINUTES);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("loginId", loginId);
		result.put("user", user);
		return result;
	}

	/**
	 * 等待二维码扫码结果的长连接
	 * 
	 * @param loginId
	 * @param session
	 * @return
	 */
	@GetMapping("login/getResponse/{loginId}")
	public @ResponseBody Map<String, Object> getResponse(@PathVariable String loginId, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		result.put("loginId", loginId);

		ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
		String user = opsForValue.get(LOGIN_KEY + loginId);
		// 长时间不扫码，二维码失效。需重新获二维码
		if (user == null) {
			result.put("success", false);
			result.put("stats", "refresh");
			return result;
		}

		// 登录扫码二维码
		if (user.equals(loginId)) {
			result.put("success", false);
			result.put("stats", "waiting");
			return result;
		}

		// 登录成,认证信息写入session
		session.setAttribute(WebSecurityConfig.SESSION_KEY, user);
		result.put("success", true);
		result.put("stats", "ok");
		return result;
	}

	/**
	 * 生成base64二维码
	 * 
	 * @param content
	 * @return
	 * @throws Exception
	 */
	private String createQrCode(String content) throws Exception {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
			hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
			hints.put(EncodeHintType.MARGIN, 1);
			BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 400, 400, hints);
			int width = bitMatrix.getWidth();
			int height = bitMatrix.getHeight();
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					image.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
				}
			}
			ImageIO.write(image, "JPG", out);
			return Base64.encodeBase64String(out.toByteArray());
		}
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		// 移除session
		session.removeAttribute(WebSecurityConfig.SESSION_KEY);
		return "redirect:/login";
	}
}
