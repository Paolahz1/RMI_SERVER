package org.example;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;
import java.io.File;

public class NodeServer {

    public static void main(String[] args) {
        try {
            // ⚙️ Datos del nodo
            String nodeId = args.length > 0 ? args[0] : "Node1";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1100;
            String basePath;

            // 📂 Si se envía un path, se usa. Si no, crear dentro del proyecto.
            if (args.length > 2) {
                basePath = args[2];
            } else {
                // Ruta relativa dentro del proyecto: data/files/<NodeId>
                String projectDir = System.getProperty("user.dir");
                basePath = projectDir + File.separator + "data" + File.separator + "files" + File.separator + nodeId;
            }

            // 🧠 Detectar IP local automáticamente
            String localIp = "127.0.0.1";
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                            localIp = addr.getHostAddress();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("No se pudo determinar la IP local: " + e.getMessage());
            }

            // 🔧 Configurar la IP del servidor RMI
            System.setProperty("java.rmi.server.hostname", localIp);

            // 🚀 Crear carpeta base si no existe
            new File(basePath).mkdirs();

            // 🖥️ Crear e iniciar el registro RMI
            NodeFileServiceImpl nodeImpl = new NodeFileServiceImpl(basePath);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind(nodeId, nodeImpl);

            // ✅ Confirmar inicio
            System.out.println("✅ Nodo RMI registrado correctamente");
            System.out.println("Nombre del nodo: " + nodeId);
            System.out.println("IP: " + localIp);
            System.out.println("Puerto: " + port);
            System.out.println("Ruta de almacenamiento: " + basePath);

        } catch (Exception e) {
            System.err.println("❌ Error al iniciar nodo RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
