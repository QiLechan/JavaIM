package org.yuezhikong.Server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.apache.logging.log4j.Level;
import org.yuezhikong.config;
import org.yuezhikong.utils.Logger;
import org.yuezhikong.utils.RSA;

public class newServer {
    public static final Logger logger = new Logger();
    private final List<user> Users = new ArrayList<>();
    //private final List<Socket> sockets = new ArrayList<>();
    private int clientIDAll = 0;
    private ServerSocket serverSocket = null;
    //服务端实例
    private static newServer instance = null;

    /**
     * @apiNote 自动创建RSA key而不替换已存在的key
     */
    private void RSA_KeyAutogenerate()
    {
        if (!(new File("Public.key").exists()))
        {
            if (!(new File("Private.key").exists()))
            {
                try {
                    RSA.generateKeyToFile("Public.key", "Private.key");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                logger.warning("系统检测到您的目录下不存在公钥，但，存在私钥，系统将为您覆盖一个新的rsa key");
                try {
                    RSA.generateKeyToFile("Public.key", "Private.key");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
        else
        {
            if (!(new File("Private.key").exists()))
            {
                logger.warning("系统检测到您的目录下存在公钥，但，不存在私钥，系统将为您覆盖一个新的rsa key");
                try {
                    RSA.generateKeyToFile("Public.key", "Private.key");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @apiNote 获取服务端实例
     * @return 服务端实例
     */
    static newServer GetInstance()
    {
        return instance;
    }

    /**
     * @apiNote 启动recvMessage线程
     * @param ClientID 客户端ID
     */
    private void StartRecvMessageThread(int ClientID)
    {
        Thread recvmessage = new RecvMessageThread(ClientID,Users);
        recvmessage.start();
        recvmessage.setName("recvMessage Thread");
    }
    /**
     * @apiNote 向所有客户端发信
     * @param inputMessage 要发信的信息
     */
    void SendMessageToAllClient(String inputMessage)
    {
        String Message = inputMessage;
        int i = 0;
        int tmpclientidall = clientIDAll;
        tmpclientidall = tmpclientidall - 1;
        Message = java.net.URLEncoder.encode(Message, StandardCharsets.UTF_8);
        try {
            while (true) {
                if (i > tmpclientidall) {
                    break;
                }
                Socket sendsocket = Users.get(i).GetUserSocket();
                PublicKey UserPublicKey = Users.get(i).GetUserPublicKey();
                if (UserPublicKey == null)
                {
                    i = i + 1;
                    continue;
                }
                if (sendsocket == null) {
                    i = i + 1;
                    continue;
                }
                Message = RSA.encrypt(Message,UserPublicKey);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sendsocket.getOutputStream()));
                writer.write(Message);
                writer.newLine();
                writer.flush();
                if (i == tmpclientidall) {
                    break;
                }
                i = i + 1;
            }
        } catch (IOException e) {
            logger.log(Level.ERROR, "SendMessage时出现IOException!");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * @apiNote 启动用户登录的线程
     */
    private void StartUserAuthThread()
    {
        Runnable UserAuthThread = () -> {
            while (true)
            {
                try {
                    assert serverSocket != null;
                    Socket clientSocket = serverSocket.accept();//接受客户端Socket请求
                    if (config.getMaxClient() >= 0)//检查是否已到最大
                    {
                        //说下这里的逻辑
                        //客户端ID 客户端数量
                        //0 1
                        //1 2
                        //2 3
                        //假如限制为3
                        //那么就需要检测接下来要写入的ID是不是2或者大于2，如果是，那就是超过了
                        if (clientIDAll >= config.getMaxClient() -1)
                        {
                            clientSocket.close();
                            continue;
                        }
                    }
                    user CureentUser = new user(clientSocket,clientIDAll);//创建用户class
                    Users.add(CureentUser);//保险措施
                    Users.set(clientIDAll,CureentUser);//添加到List中
                    StartRecvMessageThread(clientIDAll);//启动RecvMessage线程
                    clientIDAll++;//当前的最大ClientID加1
                }
                catch (IOException e)
                {
                    logger.log(Level.ERROR,"在accept时发生IOException");
                    e.printStackTrace();
                }
            }
        };
        Thread userAuthThread = new Thread(UserAuthThread);
        userAuthThread.start();
        userAuthThread.setName("UserAuthThread");
    }
    /**
     * @apiNote 启动命令系统
     */
    private void StartCommandSystem()
    {
        logger.info("服务端启动完成");
        Scanner sc = new Scanner(System.in);
        while (true)
        {
            String command;
            String[] argv;
            {
                String CommandLine = sc.nextLine();
                String[] CommandLineFormated = CommandLine.split("\\s+"); //分割一个或者多个空格
                command = CommandLineFormated[0];
                argv = new String[CommandLineFormated.length - 1];
                int j = 0;//要删除的字符索引
                int i = 0;
                int k = 0;
                while (i < CommandLineFormated.length) {
                    if (i != j) {
                        argv[k] = CommandLineFormated[i];
                        k++;
                    }
                    i++;
                }
            }

            //此时argv就是你想的那个argv，各个子指令均在这里，你可以自行更改
            //command就是命令类型
            switch (command) {
                case "help" -> {
                    logger.info("命令格式为：");
                    logger.info("kick ip 端口");
                    logger.info("say 信息");
                    logger.info("help 查看帮助");
                    logger.info("多余的空格将会被忽略");
                }
                case "say" -> {
                    StringBuilder TheServerWillSay = new StringBuilder();//服务端将要发出的信息
                    {
                        int i = 0;
                        if (0 != argv.length)
                        {
                            while (i < argv.length) {
                                if (i == argv.length - 1) {
                                    TheServerWillSay.append(argv[i]);
                                    break;
                                }
                                TheServerWillSay.append(argv[i]).append(" ");
                                i++;
                            }
                        }
                        else {
                            logger.info("您所输入的命令不正确");
                            logger.info("此命令的语法为say 信息");
                        }
                    }
                    // 发送信息
                    SendMessageToAllClient("[Server] "+TheServerWillSay);
                    logger.info("[Server] "+TheServerWillSay);
                }
                case "kick" -> {
                    if (argv.length >= 2) {
                        String IpAddress = argv[0];
                        int Port;
                        try {
                            Port = Integer.parseInt(argv[1]);
                        }
                        catch (NumberFormatException e)
                        {
                            logger.info("输入的命令语法不正确，请检查后再输入");
                            continue;
                        }
                        {
                            int i = 0;
                            int tmpclientidall = clientIDAll;
                            tmpclientidall = tmpclientidall - 1;
                            try {
                                while (true) {
                                    if (i > tmpclientidall) {
                                        logger.info("错误，找不到此用户");
                                        break;
                                    }
                                    Socket sendsocket = Users.get(i).GetUserSocket();
                                    if (sendsocket == null) {
                                        i = i + 1;
                                        continue;
                                    }
                                    if (sendsocket.getInetAddress().toString().equals("/"+IpAddress))
                                    {
                                        if (sendsocket.getPort() == Port)
                                        {
                                            logger.info("成功，已关闭IP为:"+sendsocket.getInetAddress()+"端口为："+sendsocket.getPort()+"的连接！");
                                            sendsocket.close();
                                            break;
                                        }
                                    }
                                    if (i == tmpclientidall) {
                                        logger.info("错误，找不到此用户");
                                        break;
                                    }
                                    i = i + 1;
                                }
                            } catch (IOException e) {
                                logger.log(Level.ERROR, "遍历用户时出现IOException!");
                                e.printStackTrace();
                            }
                        }
                    }
                }
                default -> logger.info("无效的命令！");
            }
        }
    }
    /**
     * @apiNote 服务端main函数
     * @param port 要开始监听的端口
     */
    public newServer(int port) {
        instance = this;
        RSA_KeyAutogenerate();
        try {
            serverSocket = new ServerSocket(port);
            /* serverSocket.setSoTimeout(10000); */
        } catch (IOException e) {
            e.printStackTrace();
        }
        StartUserAuthThread();
        StartCommandSystem();
    }
}