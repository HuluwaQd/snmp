package com.lyw.demo.util.snmp;

import com.lyw.demo.domain.Device;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;


/**
 * SNMP管理操作类
 * @author Lyw
 * @Create 2019-07-10 10:49
 */
/**
 * snmptranslate -Tp查看整个mib树
 *
 *
 * 如何找到默认的路由网关呢？
 * 查找拓扑发现程序所在计算机的SNMP MIBII中的ipRouteTable，
 * 如果发现ipRouteDest值为0.0.0.0的记录，则说明程序所在的计算机设置了默认网关，
 * ipRouteNextHop值即为默认网关的地址。
 * 然后检查默认网关的ipForwarding值。如果为1，则表明该默认网关确实是路由设备，否则不是。

 如何取得存在的子网列表呢？
 遍历路由器MIBII的IP管理组中管理对象ipRouteDest下的所有对象，以每个路由目的网络号为索引，查询ipRouteType字段的值。
 若该值为3（direct）表明为直接路由，若该值为4（indirect）则为间接路由。

 间接路由表明要通往目的网络或目的主机还要经过其它路由器，
 而直接路由表明与目的网络或目的主机直接相连，这样就可以得到与路由器直接相连的网络号。

 再根据网络号中的每条记录查询其路由掩码（ipRouteMask）。
 根据取得路由掩码，就可以确定每一个存在的网络子网的IP地址范围。

 如何发现其它的路由设备？查找默认路由网关MIBII的IP管理组路由表中类型为间接路由的路由表项，
 得到路由的下一跳地址（ipRouteNextHop）。遍历下一跳地址给出的路由设备，就可以得到更大的网络拓扑。

 如何发现网络层设备的连接关系？子网与路由器的连接关系遍历每个路由器下包含的子网来确定，
 主机与子网的关系可以通过主机IP与子网掩码来确定。
 *
 *
 * 1.3.6.1.2.1.4.21.1.1          ipRouteDest  路由器目标
 * 1.3.6.1.2.1.4.21.1.1.0.0.0.0  ipRouteDest  这个值如果为0.0.0.0则有默认网关
 * 1.3.6.1.2.1.4.21.1            ipRouteTable
 * 1.3.6.1.2.1.4.21.1.7.0.0.0.0  ipRouteNextHop 默认网关ip
 * 1.3.6.1.2.1.4.21.1.2          目标下标，网关的接口号
 * 1.3.6.1.2.1.4.21.1.7          ipRouteNextHop 下跳网关ip地址
 * 1.3.6.1.2.1.4.21.1.8          ipRouteType     路由类型  3：路由到直接子网，4：路由到非本地主机，网络或子网
 * 1.3.6.1.2.1.4.21.1.9          ipRouteProto
 * 1.3.6.1.2.1.4.21.1.11         ipRouteMask     网段的子网掩码
 * 1.3.6.1.2.1.4.20.1.1          ipAdEntAddr     网关ip地址
 *
 *
 *
 */
@SuppressWarnings("all")
public class SnmpManager implements CommandResponder{

	
    /**
     * 获取信息 snmp对象
     */
    private Snmp requestSnmp;
    /**
     * 监听trap snmp对象
     */
    private Snmp listenSnmp;
    /**
     * 发送trap snmp对象
     */
    private Snmp trapSnmp;
    /**
     * 获取信息地址
     */
    private Address requestAddress;
    /**
     * 监听地址
     */
    private Address listenAddress;
    /**
     * 获取信息IP地址(从配置文件中获取)
     */
    private String requestIp = "192.168.30.111";
    /**
     * 监听IP地址(从配置文件中获取)
     */
    private String listenIp = "192.168.30.111";
    /**
     * 获取信息端口号
     */
    private final static String requestPort = "161";
    /**
     * 监听端口号
     */
    private final String listenPort = "162";
    /**
     * 社区名(从配置文件中获取)
     */
    private String community = "public";
    /**
     * 版本号(目前默认2c)
     */
    private int version = SnmpConstants.version2c;
    /**
     * 社区目的地
     */
    private  CommunityTarget communityTarget;

    /**
     * 监测对象的系统(需针对设备进行具体调试)
     */
    private static String systemcore = "windows";

    public SnmpManager(String requestIp){
        this.requestIp = requestIp;
    }

    /**
     * 获取拓扑图信息
     */
    public static void getTopologicalGraph(String ip) throws IOException {
        //默认网关
        String ipRouteNextHop = "1.3.6.1.2.1.4.21.1.7.0.0.0.0";
        //ipRouteTable
        String ipRouteDest = "1.3.6.1.2.1.4.21.1.1.0.0.0.0";
        String allIpRouteDest = "1.3.6.1.2.1.4.21.1.1";
        //ipForwarding
        String ifForwarding = "";
        //ipRouteType
        String ipRouteType = "1.3.6.1.2.1.4.21.1.8";
        //ipRouteMask
        String ipRouteMask = "1.3.6.1.2.1.4.21.1.11 ";
        SnmpManager snmp = new SnmpManager(ip);
        snmp.snmpRequestInit();
        String result = snmp.snmpGetRequest(ipRouteDest);
        if(result!=null&&!"".equals(result)){
            if(result.equals("0.0.0.0")){
                //存在默认网关,获取默认网关
                String mrwg = snmp.snmpGetRequest(ipRouteNextHop);
                System.out.println("上级网关为:"+mrwg);
                //通过默认网关ip查询其MIB中的ipForwarding,为1则为路由器
                SnmpManager wgsnmp = new SnmpManager(mrwg);
                //String wgresult = wgsnmp.snmpGetRequest(ifForwarding);
                //if(wgresult!=null&&!"".equals(wgresult)){
                    //if(wgresult.equals("1")){
                        System.out.println("上级网关是路由器");
                        //查找路由器所有子网
                        //String[] oids = {"1.3.6.1.2.1.4.21.1.2"};
                        String[] oids = {allIpRouteDest,ipRouteType,ipRouteMask};
                        //snmp暂时测试本机
                        List<TableEvent> tableEvents = snmp.snmpWalk(oids);
                        //除去0.0.0.0默认网关
                        //tableEvents.remove(0);
                        //拿所有子网数据
                        for (TableEvent tableEvent : tableEvents) {
                            VariableBinding[] columns = tableEvent.getColumns();

                            System.out.println("IP:"+columns[0].getVariable().toString());
                            System.out.println("子网类型:"+columns[1].getVariable().toString());
                            System.out.println("子网掩码:"+columns[2].getVariable().toString());

                        }


                    //}else{
                       // System.out.println("默认网关不是路由器");
                    //}
//                }else{
//                    System.out.println("ifForwarding null");
//                }

            }else{
                System.out.println("没有设置默认网关");
            }
        }
    }


    /**
     * 批量检测ip设备信息
     * @param devs
     * @return
     * @throws IOException 
     */
    public static Map<String, Map<String, Object>> getIpsInfo(List<Device> devs) throws IOException {
    	HashMap<String, Object> rateMap = new HashMap();
        HashMap<String, Map<String, Object>> ipMap = new HashMap();
    	
        for (Device dev : devs) {
            SnmpManager snmp = new SnmpManager(dev.getIp());
            //判断设备系统
            String sys = snmp.snmpGetRequest("1.3.6.1.2.1.1.1.0");
            if(sys==null){
            	ipMap.put(dev.getId().toString(),null);
            }else{
            	if(sys.contains("Windows")){
            		systemcore = "windows";
            	}else if(sys.contains("Linux")){
            		systemcore = "linux";
            	}
            	//检测cpu使用率
                double cpuUsedRate = snmp.getCpuUsedRate();
                //检测存储使用率
                rateMap = snmp.getDiskUsedRate();
                if(rateMap!=null){
                	rateMap.put("CPU使用率",(int)(cpuUsedRate*100));
                }
                ipMap.put(dev.getId().toString(),rateMap);
            }
            //必须关闭snmp，否则jvm内存会溢出
            snmp.requestSnmp.close();
        }
    	return ipMap;

        
    }


    /**
     * OID查询初始化方法
     */
    private void snmpRequestInit(){
        //初始化snmp对象中的目标地址
        String udpAdress = "udp:"+requestIp+"/"+requestPort;
        requestAddress = GenericAddress.parse(udpAdress);
        TransportMapping transportMapping = null;
        try {
            transportMapping = new DefaultUdpTransportMapping();
            requestSnmp = new Snmp(transportMapping);
            requestSnmp.listen();//创建一个线程，占用JVM内存
            
            // 设置目标地址,社区名称
            communityTarget = new CommunityTarget();
            communityTarget.setCommunity(new OctetString(community));
            communityTarget.setAddress(requestAddress);
            // 通信不成功重复次数
            communityTarget.setRetries(2);
            // 超时时间
            communityTarget.setTimeout(2*1000);
            // 设置版本
            communityTarget.setVersion(version);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 监听初始化方法
     */
    private Snmp doListenInit() throws IOException {
        //创建接收SnmpTrap的线程池，参数： 线程名称及线程数
        ThreadPool threadPool = ThreadPool.create("Trap", 2);
        MultiThreadedMessageDispatcher dispatcher = new MultiThreadedMessageDispatcher(threadPool,
                new MessageDispatcherImpl());
        //监听端的 ip地址 和 监听端口号
        String listenAddressStr = "udp:"+listenIp+"/"+listenPort;
        Address listenAddress = GenericAddress.parse(listenAddressStr);
        TransportMapping<?> transport;
        if (listenAddress instanceof UdpAddress) {
            transport = new DefaultUdpTransportMapping((UdpAddress)listenAddress);
        }else{
            transport = new DefaultTcpTransportMapping((TcpAddress) listenAddress);
        }
        listenSnmp = new Snmp(dispatcher, transport);
        //设置可以接收三个版本的trap信息
        listenSnmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
        listenSnmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
        //MPv3.setEnterpriseID(35904);
        listenSnmp.getMessageDispatcher().addMessageProcessingModel(new MPv3());


//        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3
//                .createLocalEngineID()),0);
//
//        SecurityModels.getInstance().addSecurityModel(usm);
//        // 添加安全协议,如果没有发过来的消息没有身份认证,可以跳过此段代码
//        SecurityProtocols.getInstance().addDefaultProtocols();
//        // 创建和添加用户
//        OctetString userName1 = new OctetString(username1);
//        OctetString userName2 = new OctetString(username2);
//        //OctetString userName3 = new OctetString(username3);
//        //OctetString userName4 = new OctetString(username4);
//        OctetString authPass = new OctetString(authPassword);
//        OctetString privPass = new OctetString("privPassword");
//        UsmUser usmUser1 = new UsmUser(userName1, AuthMD5.ID, authPass, PrivDES.ID, privPass);
//        UsmUser usmUser2 = new UsmUser(userName2, AuthMD5.ID, authPass, PrivDES.ID, privPass);
//        //UsmUser usmUser3 = new UsmUser(userName3, AuthMD5.ID, authPass, PrivDES.ID, privPass);
//        //UsmUser usmUser4 = new UsmUser(userName4, AuthMD5.ID, authPass, PrivDES.ID, privPass);
//        //因为接受的Trap可能来自不同的主机，主机的Snmp v3加密认证密码都不一样，所以根据加密的名称，来添加认证信息UsmUser。
//        //添加了加密认证信息的便可以接收来自发送端的信息。
//        UsmUserEntry userEnty1 = new UsmUserEntry(userName1,usmUser1);
//        UsmUserEntry userEnty2 = new UsmUserEntry(userName2,usmUser2);
//        //UsmUserEntry userEnty3 = new UsmUserEntry(userName3,usmUser3);
//        //UsmUserEntry userEnty4 = new UsmUserEntry(userName4,usmUser4);
//        UsmUserTable userTable = snmp.getUSM().getUserTable();
//        // 添加其他用户
//        userTable.addUser(userEnty1);
//        userTable.addUser(userEnty2);

        return listenSnmp;

    }

    /**
     * trap发送初始化
     */
    private void sendTrapInit() throws IOException {
        //目标主机的ip地址 和 端口号
        String listenAddresStr = "udp:"+listenIp+"/"+listenPort;
        listenAddress = GenericAddress.parse(listenAddresStr);
        DefaultUdpTransportMapping transportMapping = new DefaultUdpTransportMapping();
        trapSnmp = new Snmp(transportMapping);
        transportMapping.listen();
    }

    /**
     * 发送trap
     * @param message trap信息
     */
    public void sendTrap(String message) throws IOException {
        //初始化trap snmp
        sendTrapInit();
        PDU pdu = new PDU();
        //封装消息
        VariableBinding v = new VariableBinding();
        //v.setOid(SnmpConstants.sysName);
        v.setVariable(new OctetString(message));
        pdu.add(v);
        pdu.setType(PDU.TRAP);
        //设置目的地
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setAddress(listenAddress);
        //重连次数和超时时间
        target.setRetries(2);
        target.setTimeout(1500);
        target.setVersion(version);
        //发送
        trapSnmp.send(pdu, target);
    }

    /**
     * 监听方法
     * @throws IOException
     */
    public void doListen() throws IOException {
        Snmp listenSnmp = doListenInit();
        listenSnmp.listen();
        listenSnmp.addCommandResponder(this);
    }

    /**
     * 1.3.6.1.2.1.25.2.3.1.3  存储设备名称
     *
     * 获取磁盘使用率
     *  内存使用情况:
     *       总大小(kb)    1.3.6.1.2.1.25.2.2.0
     *      已使用存储单元个数
     *               1:   1.3.6.1.2.1.25.2.3.1.6.1  C
     *               2:   1.3.6.1.2.1.25.2.3.1.6.2  D
     *               3:   1.3.6.1.2.1.25.2.3.1.6.3  虚拟
     *               4:   1.3.6.1.2.1.25.2.3.1.6.4  物理
     *
     *      总存储单元个数
     *               1：C盘   1.3.6.1.2.1.25.2.3.1.5.1
     *               2：D盘   1.3.6.1.2.1.25.2.3.1.5.2
     *            3：虚拟内存 1.3.6.1.2.1.25.2.3.1.5.3
     *            4：物理内存 1.3.6.1.2.1.25.2.3.1.5.4
     *
     *      存储单元大小(kb)  1.3.6.1.2.1.25.2.3.1.4
     *      存储设备类型      1.3.6.1.2.1.25.2.3.1.1 (4固定磁盘 2物理内存 3虚拟内存)
     */
    
    /*
     * 
     * */
    private HashMap<String, Object> getDiskUsedRate() throws IOException {
    	//1.3.6.1.4.1.2021.4.6.0  linux内存剩余kb
    	//1.3.6.1.4.1.2021.4.5.0  linux内存总大小kb
    	//1.3.6.1.4.1.2021.9.1.6.1 linux / 磁盘总大小kb
    	//1.3.6.1.4.1.2021.9.1.8.1 linux / 磁盘已用kb
    	if(requestSnmp==null){
            snmpRequestInit();
        }
    	HashMap<String,Object> map = new HashMap();
    	if(systemcore.equals("windows")){
    		String[] oids = {
                    "1.3.6.1.2.1.25.2.3.1.3",//存储设备名称
                    "1.3.6.1.2.1.25.2.3.1.6",//存储设备已使用存储单元个数
                    "1.3.6.1.2.1.25.2.3.1.5",//存储设备总存储单元个数
                    "1.3.6.1.2.1.25.2.3.1.4",//存储单元大小
                    "1.3.6.1.2.1.25.2.3.1.2", //存储设备类型
                    "1.3.6.1.2.1.1.1.0"//系统类型
            };
            List<TableEvent> tableEvents = snmpWalk(oids);
            map = analysisDiskUsedRate(tableEvents);
        }else if(systemcore.equals("linux")){
        	String memFree = snmpGetRequest("1.3.6.1.4.1.2021.4.6.0");
        	String memTotal = snmpGetRequest("1.3.6.1.4.1.2021.4.5.0");
        	String diskTotal = snmpGetRequest("1.3.6.1.4.1.2021.9.1.6.1");
        	String diskUsed = snmpGetRequest("1.3.6.1.4.1.2021.9.1.8.1");
        	if(memFree==null||memTotal==null||diskTotal==null||diskUsed==null
        			||memFree.equals("noSuchObject")||memTotal.equals("noSuchObject")
        			||diskTotal.equals("noSuchObject")||diskUsed.equals("noSuchObject")){
        		map = null;
        	}else{
        		//double memRate = (double)(int)(((double)100-(Double.parseDouble(memFree)/Double.parseDouble(memTotal)))*10)/10;
            	double memRate = new BigDecimal(100-(Double.parseDouble(memFree)/Double.parseDouble(memTotal))).setScale(2, RoundingMode.UP).doubleValue();
            	double diskRate = new BigDecimal(Double.parseDouble(diskUsed)/Double.parseDouble(diskTotal)*100).setScale(2, RoundingMode.UP).doubleValue();
            	HashMap<String, Double> diskMap = new HashMap<String,Double>();
            	HashMap<String, Double> memMap = new HashMap<String,Double>();
            	diskMap.put("总大小",new BigDecimal(Double.parseDouble(diskTotal)/(1024*1024)).setScale(2, RoundingMode.UP).doubleValue());
            	diskMap.put("已使用",new BigDecimal(Double.parseDouble(diskUsed)/(1024*1024)).setScale(2, RoundingMode.UP).doubleValue());
            	diskMap.put("使用率",(double)(int)diskRate);
            	memMap.put("总大小", new BigDecimal(Double.parseDouble(memTotal)/(1024*1024)).setScale(2, RoundingMode.UP).doubleValue());
            	memMap.put("已使用", new BigDecimal((Double.parseDouble(memTotal)-Double.parseDouble(memFree))/(1024*1024)).setScale(2, RoundingMode.UP).doubleValue());
            	memMap.put("使用率", (double)(int)memRate);
            	
        		map.put("物理内存", memMap);
            	map.put("磁盘", diskMap);
        	}
        }	
        return map;
    }

    /**
     * 获取Cpu使用率
     *
     */
    private double getCpuUsedRate() throws IOException {
        if(requestSnmp==null){
            snmpRequestInit();
        }
        //String getCpuOid = ".1.3.6.1.2.1.25.3.3.1.2";
        //1.3.6.1.4.1.2021.11.11.0  linux系统空闲比
        
        double rate = 0;
        if(systemcore.equals("windows")){
        	String[] oids = {"1.3.6.1.2.1.25.3.3.1.2"};
            List<TableEvent> tableEvents = snmpWalk(oids);
            rate = analysisCpuUsedRate(tableEvents);
        }else if(systemcore.equals("linux")){
        	String request = snmpGetRequest("1.3.6.1.4.1.2021.11.11.0");
        	if(request==null||request.equals("noSuchInstance")){
        		rate = 0d;
        	}else{
        		rate = (double)(100-Integer.parseInt(request))/100;
        	}
        }
        return rate;
    }


    /**
     * getRequest方式获取信息
     * @param oid
     * @return
     * @throws IOException
     */
    private String snmpGetRequest(String oid) throws IOException {
        //添加oid参数
    	if(requestSnmp==null){
            snmpRequestInit();
        }
        PDU pdu = new PDU();
        OID id = new OID(oid);
        pdu.add(new VariableBinding(id));
        //设置请求方式
        pdu.setType(PDU.GET);
        ResponseEvent event = requestSnmp.send(pdu,communityTarget);

        //对结果进行解析
        return readResponse(event, id);

    }

    /**
     * getRequest解析response
     * @param event 回复对象
     * @param oid 对应的oid
     * @return
     */
    private String readResponse(ResponseEvent event,OID oid){
        //直接取出值
        if (null!=event&&event.getResponse()!=null){
            PDU response = event.getResponse();
            //Vector<VariableBinding> vector= (Vector<VariableBinding>) response.getVariableBindings();
            Variable variable = response.getVariable(oid);
            String value = variable.toString();
            return value;

        }else {
            return null;
        }
    }

    /**
     * snmpWalk请求
     * @param oid
     * @return
     */
    private List<TableEvent> snmpWalk(String[] oids){
        //如果没有变量可以查询则返回一个变量名+endOfMibView的内容

        // 设置TableUtil的工具
        TableUtils utils=new TableUtils(requestSnmp,new DefaultPDUFactory(PDU.GETBULK));
        utils.setMaxNumRowsPerPDU(2);
        OID[] OIDs = new OID[oids.length];
        for (int i = 0;i<oids.length;i++) {
            OID clounmOid = new OID(oids[i]);
            OIDs[i] = clounmOid;
        }
        // 获取查询结果list,new OID("0"),new OID("40")设置输出的端口数量
        //某一个OID节点下，如磁盘，可能有多个磁盘，按照给定的请求方式，有几个数据(组件)就存在几个端口，
        //每一行返回的数据是只一个OID节点下的所有数据，而下方给定的端口数量，则是允许输出一个OID节点下的多少个端口
        List<TableEvent> list = utils.getTable(communityTarget, OIDs,new OID("0"),new OID("100"));

        return list;
    }

    /**
     * 解析cpu结果
     * @param list 经过snmpwalk请求后返回的list数据
     */
    private double analysisCpuUsedRate(List<TableEvent> list){
        int cpuLoad = 0;
        for (int i = 0; i < list.size(); i++) {
            // 取list中的一行
            TableEvent te = list.get(i);
            // 对每一行结果进行再次拆分
            VariableBinding[] vb = te.getColumns();
            if (vb != null) {
                for (int j = 0; j < vb.length; j++) {
                    cpuLoad += vb[j].getVariable().toInt();
                }
            } else {
            	return 0d;
            }
        }
        double size = list.size();

        return ((double) cpuLoad)/size/100;
    }

    /**
     * 解析磁盘以及内存结果
     * @param tableEvents 经过snmpwalk请求后返回的list数据
     * @return
     */
    private HashMap<String, Object> analysisDiskUsedRate(List<TableEvent> tableEvents){
        HashMap<String, Object> diskMap = new HashMap<>();
        for (TableEvent tableEvent : tableEvents) {
            VariableBinding[] columns = tableEvent.getColumns();
            if(columns!=null){
            	String name = columns[0].getVariable().toString();
                double used = columns[1].getVariable().toLong();
                double total = columns[2].getVariable().toLong();
                double size = columns[3].getVariable().toLong();
                String type = columns[4].getVariable().toString();
                char[] chars = type.toCharArray();
                boolean flag = true;
                if(name.contains("Physical")&&chars[chars.length-1]=='2'){
                    diskMap.put("物理内存",calculation(used,total,size));
                    flag =false;
                }else if(name.contains("Virtual")&&chars[chars.length-1]=='3'){
                    diskMap.put("虚拟内存",calculation(used,total,size));
                    flag =false;
                }  
                //如果物理内存和虚拟内存被读取过了就不会再读取
                
                if(flag){
                	if((name.toCharArray()[0]+"").equals("C")){
                		diskMap.put("磁盘",calculation(used,total,size));
                	}else{
                		diskMap.put(name.toCharArray()[0]+"盘",calculation(used,total,size));
                	}
                }
                
            }else{
            	return null;
            }
            

        }


        return diskMap;
    }

    /**
     * 计算
     * @param used 已使用存储点数量
     * @param total 总存储点数量
     * @param size 存储点大小
     * @return
     */
    private HashMap<String, Double> calculation(double used,double total,double size){
        HashMap<String, Double> detail = new HashMap<>();
        double rate = (double) used/(double) total;
        detail.put("总大小", (double)((int)((total*size/(double) (1024*1024*1024))*10))/10);
        detail.put("已使用", (double)((int)((used*size/(double) (1024*1024*1024))*10))/10);
        detail.put("使用率",(double)(int)(rate*100));
        return detail;
    }

    /**
     * 监听到trap后会自动进入此方法
     * @param commandResponderEvent 接收到的消息
     */
    @Override
    public void processPdu(CommandResponderEvent commandResponderEvent) {
        System.out.println("收到消息,正在解析");
        // 解析Response
        if (commandResponderEvent != null && commandResponderEvent.getPDU() != null) {

            Vector<VariableBinding> recVBs = (Vector<VariableBinding>) commandResponderEvent.getPDU().getVariableBindings();
            for (int i = 0; i < recVBs.size(); i++) {
                VariableBinding recVB = recVBs.elementAt(i);
                System.out.println(recVB.getOid() + " : " + recVB.getVariable());
            }
        }else{
            System.out.println("消息为空:commandResponderEvent：null");
        }
    }
}
