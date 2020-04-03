package com.example.jsch;

import com.jcraft.jsch.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mingxuan.ye
 * @since 2020/4/3
 * <p>
 * JSch 支持SSH-2.0-OpenSSH_5.3 协议
 * 使用JSch 连接shell、ChannelSftp上传文件、并使用ChannelExec完成交互。
 * </P>
 */
public class JSchUtil {
    private static Pattern compile = Pattern.compile("^\\w+(?=\\s)");
    /*
     * 主题操作流程如下。
     * 1.获取此次连接linux 的session
     * 2.上传文件到对应目录
     * 3.获取文件MD5
     * 4.解压上传的文件
     * 5.执行压缩包里的脚本（这里应该自己就会写了，获取服务器返回字符串、自己正则匹配去找到对应名称）
     *
     */

    public static void main(String[] args) {
        Session session = connectSSH("255.255.255.255", 22, "root", "123456");
        boolean uploadFile = channelSftpUploadFile(session, null, null, null);
        //多次交互 可以学习一下 channelShell、此处用ChannelExec
        String linuxResult = getLinuxResult(session, null, null);
        boolean entryDirectoryToDecompress = entryDirectoryToDecompress(session, null);
        closeSession(session);
    }

    /**
     * 连接配置
     *
     * @param hostIp   ip地址
     * @param port     端口号
     * @param username 用户名称
     * @param password 用户密码
     * @return Session 一次会话
     */
    public static Session connectSSH(String hostIp, int port, String username, String password) {
        JSch jsch = new JSch();
        Session session = null;
        try {
            // 不解释了吧 抓紧上车
            session = jsch.getSession(username, hostIp, port);
            session.setPassword(password);
            //听说这一步骤是设置服务器不询问你是否保存密码   其实我就是临时工、多一次交互就多一次麻烦
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            //设置连接时长  单位毫秒
            int time = 30 * 1000;
            session.connect(time);
            session.setTimeout(time);
        } catch (JSchException e) {
            e.printStackTrace();
        }
        return session;
    }

    /**
     * 关闭此次会话
     *
     * @param session 会话
     */
    private static void closeSession(Session session) {
        if (session != null) {
            session.disconnect();
        }
    }

    /**
     * 上传到指定目录下
     *
     * @param session   会话
     * @param is        输入流
     * @param targetDir 目标文件
     * @param fileName  文件名称
     * @return
     */
    public static boolean channelSftpUploadFile(Session session, InputStream is, String targetDir, String fileName) {
        ChannelSftp channelSftp = null;
        try {
            channelSftp = (ChannelSftp) session.openChannel("sftp");
//            channelSftp.chmod();权限 、需要的自己加 我这里直接上传
            channelSftp.connect();
            //进入目录
            channelSftp.cd(targetDir);
            //上传文件
            channelSftp.put(is, fileName, ChannelSftp.OVERWRITE);
            //channelSftp.getExitStatus() 此方法返回值我还没玩转、需要的自己去测试一下。
            //一般不抛异常 我这里直接return true;
            return channelSftp.getExitStatus() > -1;
        } catch (JSchException e) {
            //连接出的异常
            e.printStackTrace();
        } catch (SftpException e) {
            //此方法channelSftp.cd()抛出的异常
            e.printStackTrace();
        } finally {
            if (channelSftp != null) {
                channelSftp.disconnect();
            }
        }
        return false;
    }

    /**
     * 获取linux返回值
     *
     * @param session   会话
     * @param targetDir 目标目录
     * @param fileName  文件名称
     * @return String
     */
    public static String getLinuxResult(Session session, String targetDir, String fileName) {
        ChannelExec channelExec = null;
        InputStream is = null;
        String result = null;
        try {
            channelExec = (ChannelExec) session.openChannel("exec");
            //我这里需要知道  之前上传文件的MD5值 所以直接输入命令
            channelExec.setCommand("md5sum " + targetDir + "/" + fileName);
            channelExec.connect();
            //连接后就可以读服务器返回的输入流了。
            is = channelExec.getInputStream();
            result = bufferReader(is);
        } catch (JSchException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeChannelExec(channelExec);
        }
        return result;
    }

    /**
     * 去解压文件 赋予权限
     *
     * @param session   会话
     * @param targetDir 目标目录
     * @return true
     */
    public static boolean entryDirectoryToDecompress(Session session, String targetDir) {
        ChannelExec channelExec = null;
        try {
            channelExec = (ChannelExec) session.openChannel("exec");
            //命令自己组合。感兴趣了解一下linux的命令 我这里用2个分号 标识这是这次命令
            String command = " cd " + targetDir + "; chmod -R 777 " + targetDir + ";";
            channelExec.connect();
            return channelExec.getExitStatus() > -1;
        } catch (JSchException e) {
            //连接出的异常
            e.printStackTrace();
        } finally {
            closeChannelExec(channelExec);
        }
        return false;
    }

    private static void closeChannelExec(ChannelExec channelExec) {
        if (channelExec != null) {
            channelExec.disconnect();
        }
    }


    private static String bufferReader(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String result = null;
        try {
            Matcher matcher = compile.matcher(bufferedReader.readLine());
            //我这里直接获取到要的值了。不需要循环去读。 有需要的自己去连接一下看看需要什么内容
            if (matcher.find()) {
                result = matcher.group(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }


}
