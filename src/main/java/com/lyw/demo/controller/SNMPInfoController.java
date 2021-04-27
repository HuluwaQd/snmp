package com.lyw.demo.controller;

import com.lyw.demo.domain.Device;
import com.lyw.demo.util.snmp.SnmpManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            Device dev = new Device();
            dev.setId(1L);
            dev.setIp("192.168.30.111");
            devs.add(dev);
            return SnmpManager.getIpsInfo(devs);

        }catch (Exception e){
            e.printStackTrace();
            return null;
        }

    }
}
