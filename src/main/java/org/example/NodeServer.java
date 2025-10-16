package org.example;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Servidor RMI que registra un nodo remoto con una implementación de NodeFileService.
 */
public class NodeServer {

    public static void main(String[] args) {
        try {
            String nodeId = args.length > 0 ? args[0] : "Node1";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1100;
            String basePath = args.length > 2 ? args[2] : "/data/files/" + nodeId;


            NodeFileServiceImpl nodeImpl = new NodeFileServiceImpl(basePath);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind(nodeId, nodeImpl);

            System.out.println("Nodo RMI registrado como '" + nodeId + "' en puerto " + port);
            System.out.println("BasePath: " + basePath);
        } catch (Exception e) {
            System.err.println("Error al iniciar nodo RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
