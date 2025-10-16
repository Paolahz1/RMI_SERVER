package org.example;

import org.example.infrastructure.remote.NodeFileService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NodeFileServiceImpl extends UnicastRemoteObject implements NodeFileService {

    private static final Logger LOGGER = Logger.getLogger(NodeFileServiceImpl.class.getName());

    private final ExecutorService threadPool;
    private final int THREAD_POOL_SIZE = 10;

    private final String basePath;

    public NodeFileServiceImpl(String basePath) throws RemoteException {
        super();
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.basePath = basePath;
        new File(basePath).mkdirs();
    }

    /**
     * Obtiene la ruta de carpeta del usuario.
     * Estructura: /data/files/userId/
     */
    private String getUserPath(Long userId) {
        return basePath + File.separator + userId;
    }

    /**
     * Obtiene la ruta completa del archivo.
     * Estructura: /data/files/userId/fileId
     */
    private String getFilePath(Long userId, String fileId) {
        return getUserPath(userId) + File.separator + fileId;
    }

    @Override
    public boolean createDirectory(String ownerId, String path) throws RemoteException {
        // La jerarquía de directorios es lógica (en BD), no física en el nodo
          return true;
    }

    /**
     * Sube un archivo en la carpeta del usuario.
     *
     * @param fileId UUID del archivo (formato: "userId-uuid")
     * @param content bytes del archivo
     */
    @Override
    public boolean uploadFile(String fileId, byte[] content) throws RemoteException {
        LOGGER.info("Recibiendo archivo: " + fileId + ", tamaño: " + content.length + " bytes");

        Future<Boolean> future = threadPool.submit(() -> {
            try {
                // Extraer userId del fileId
                // Formato esperado: "userId-uuid" (ej: "1-550e8400-e29b-41d4-a716-446655440000")
                Long userId = extractUserIdFromFileId(fileId);

                if (userId == null) {
                    return false;
                }

                // Crear carpeta del usuario si no existe
                String userPath = getUserPath(userId);
                File userDir = new File(userPath);
                if (!userDir.exists()) {
                    userDir.mkdirs();
                    LOGGER.info("Carpeta del usuario creada: " + userPath);
                }

                // Guardar archivo en la carpeta del usuario
                String filePath = getFilePath(userId, fileId);
                File targetFile = new File(filePath);

                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(content);
                }

                return true;

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error al guardar archivo: " + fileId, e);
                return false;
            }
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Error en uploadFile", e);
            throw new RemoteException("Error procesando uploadFile", e);
        }
    }

    /**
     * Descarga un archivo de la carpeta del usuario.
     *
     * @param fileId UUID del archivo (formato: "userId-uuid")
     */
    @Override
    public byte[] downloadFile(String fileId) {

        Future<byte[]> future = threadPool.submit(() -> {
            try {
                // Extraer userId del fileId
                Long userId = extractUserIdFromFileId(fileId);

                if (userId == null) {
                    LOGGER.warning("No se pudo extraer userId de: " + fileId);
                    return null;
                }

                String filePath = getFilePath(userId, fileId);
                File targetFile = new File(filePath);

                if (!targetFile.exists()) {
                    LOGGER.warning("Archivo no encontrado: " + filePath);
                    return null;
                }

                byte[] data = Files.readAllBytes(targetFile.toPath());
                return data;

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error leyendo archivo: " + fileId, e);
                return null;
            }
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Error en downloadFile", e);
            return null;
        }
    }

    /**
     * Descarga múltiples archivos de carpetas de usuario.
     *
     * @param filePaths lista de fileIds (formato: "userId-uuid")
     */
    @Override
    public List<byte[]> downloadFiles(List<String> filePaths) throws RemoteException {

        List<Future<byte[]>> futures = new ArrayList<>();

        for (String fileId : filePaths) {
            Future<byte[]> future = threadPool.submit(() -> {
                try {
                    Long userId = extractUserIdFromFileId(fileId);
                    if (userId == null) {
                        LOGGER.warning("UserId inválido para: " + fileId);
                        return null;
                    }

                    String filePath = getFilePath(userId, fileId);
                    File targetFile = new File(filePath);

                    if (!targetFile.exists()) {
                        LOGGER.warning("Archivo no encontrado: " + filePath);
                        return null;
                    }

                    return Files.readAllBytes(targetFile.toPath());

                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error leyendo archivo: " + fileId, e);
                    return null;
                }
            });
            futures.add(future);
        }

        List<byte[]> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                byte[] content = futures.get(i).get();
                if (content != null) {
                    LOGGER.fine("✓ Archivo " + filePaths.get(i) + " leído: " + content.length + " bytes");
                } else {
                    LOGGER.warning("Archivo " + filePaths.get(i) + " retorna null");
                }
                results.add(content);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "Error procesando archivo: " + filePaths.get(i), e);
                results.add(null);
            }
        }

        return results;
    }

    /**
     * Elimina un archivo de la carpeta del usuario.
     *
     * @param fileId UUID del archivo (formato: "userId-uuid")
     */
    @Override
    public boolean deleteFile(String fileId) throws RemoteException {
        LOGGER.warning("LLEGAAA Request to delete file " + fileId);
        System.out.println("LLEGAAA Request to delete file: " + fileId);
        try {
            Long userId = extractUserIdFromFileId(fileId);
            if (userId == null) {
                LOGGER.warning("UserId inválido para eliminar: " + fileId);
                return false;
            }

            String filePath = getFilePath(userId, fileId);
            File targetFile = new File(filePath);

            if (targetFile.exists()) {
                boolean deleted = targetFile.delete();
                if (!deleted) {
                    LOGGER.warning("No se pudo eliminar: " + filePath);
                }
                return deleted;
            } else {
                LOGGER.warning("Archivo no existe: " + filePath);
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error eliminando archivo: " + fileId, e);
            return false;
        }
    }

    /**
     * Extrae el userId del fileId.
     * Formato esperado: "userId-uuid" (ej: "1-550e8400-e29b-41d4-a716-446655440000")
     *
     * @param fileId identificador del archivo
     * @return userId o null si el formato es inválido
     */
    private Long extractUserIdFromFileId(String fileId) {
        try {
            // El fileId tiene formato: "userId-uuid"
            // Extraer la parte antes del primer "-"
            int firstDash = fileId.indexOf('-');
            if (firstDash <= 0) {
                LOGGER.warning("Formato inválido de fileId: " + fileId);
                return null;
            }

            String userIdStr = fileId.substring(0, firstDash);
            return Long.parseLong(userIdStr);

        } catch (NumberFormatException e) {
            LOGGER.warning("No se pudo parsear userId de: " + fileId);
            return null;
        }
    }

    // Métodos no implementados (retornan false o listas vacías)

    @Override
    public boolean deleteFiles(List<String> filePaths) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteDirectory(String directoryPath) throws RemoteException {
        return false;
    }

    @Override
    public boolean moveFile(String sourcePath, String destinationPath) throws RemoteException {
        return false;
    }

    @Override
    public boolean renameFile(String currentPath, String newName) throws RemoteException {
        return false;
    }

    @Override
    public boolean moveDirectory(String sourcePath, String destinationPath) throws RemoteException {
        return false;
    }

    @Override
    public boolean shareFile(String filePath, String targetUser) throws RemoteException {
        return false;
    }

    @Override
    public boolean shareFiles(List<String> filePaths, String targetUser) throws RemoteException {
        return false;
    }

    @Override
    public boolean shareDirectory(String directoryPath, String targetUser) throws RemoteException {
        return false;
    }

    @Override
    public List<String> listFiles(String directoryPath) throws RemoteException {
        return List.of();
    }

    @Override
    public boolean exists(String path) throws RemoteException {
        return false;
    }

    @Override
    public boolean isDirectory(String path) throws RemoteException {
        return false;
    }
}