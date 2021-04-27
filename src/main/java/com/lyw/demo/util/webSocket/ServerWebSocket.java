package com.lyw.demo.util.webSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 主页服务器巡检websocket长连接
 * @author lyw
 * 
 * 2019年8月29日
 */
@ServerEndpoint("/serverWebSocket/{username}")
public class ServerWebSocket {

	 private static Logger xjlogger = LoggerFactory.getLogger("xjlogger");
	 private static Map<Session,String> sessionMap = new HashMap();
	 @OnOpen//打开连接执行
	 public void onOpw(@PathParam("username")String username,Session session) {
		 
		 //当有一个新连接时,将连接数据存入map中
		 sessionMap.put(session,username);
		 xjlogger.info(username+"建立主页服务器巡检连接");
	 }
	 @OnMessage//收到消息执行
	 public void onMessage(String message,Session session) {
		 xjlogger.info(sessionMap.get(session)+"发送消息:"+message);
	 }
	 @OnClose//关闭连接执行
	 public void onClose(Session session) {
		 xjlogger.info(sessionMap.get(session)+"主页服务器巡检连接已关闭");
		 sessionMap.remove(session);
	 }
	 @OnError//连接错误的时候执行
	 public void onError(Throwable error,Session session) {
		 xjlogger.error(sessionMap.get(session)+"主页服务器巡检连接错误"+error.getMessage());
	 }
       /*
       	websocket  session发送文本消息有两个方法：getAsyncRemote()和
      	getBasicRemote()  getAsyncRemote()和getBasicRemote()是异步与同步的区别，
      	大部分情况下，推荐使用getAsyncRemote()。
       */
	 public static synchronized void sendMessage(String message) throws IOException{
		Set<Session> keySet = sessionMap.keySet();
		for (Session session : keySet) {
			xjlogger.info("向("+sessionMap.get(session)+")发送了主页服务器巡检消息");
			session.getAsyncRemote().sendText(message);
		}
		 
	 }

}
