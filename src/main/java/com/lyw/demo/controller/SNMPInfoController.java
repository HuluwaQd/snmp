package com.lyw.demo.controller;

import com.lyw.demo.domain.Device;
import com.lyw.demo.util.snmp.SnmpManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SNMP Contorller
 * @author lyw
 * 2020-11-11 11:00
 */
@RestController
@RequestMapping("/snmp")
@Slf4j
public class SNMPInfoController {


    @GetMapping("/computer/info")
    public Map<String, Map<String, Object>> getComputerInfo(){
        try {
            List<Device> devs = new ArrayList<>();
            Device dev1 = new Device();
            dev1.setId(1L);
            dev1.setIp("192.168.30.111");
            Device dev2 = new Device();
            dev2.setId(2L);
            dev2.setIp("192.168.34.5");
            devs.add(dev1);
            devs.add(dev2);
            return SnmpManager.getIpsInfo(devs);

        }catch (Exception e){
            e.printStackTrace();
            return null;
        }

    }

    @GetMapping("/trap")
    public void sendTrap() throws IOException {
        SnmpManager snmpManager = new SnmpManager("192.168.30.111");
        snmpManager.sendTrap("traptest");
    }

    public static void main(String[] args) throws IOException {
        SnmpManager snmpManager = new SnmpManager("192.168.30.111");
        snmpManager.doListen();
    }
}
