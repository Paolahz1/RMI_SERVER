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

        // ✅ Si no se especifica basePath, usar carpeta "data/files" dentro del proyecto
        if (basePath == null || basePath.isBlank()) {
            String projectDir = System.getProperty("user.dir"); // carpeta raíz del proyecto
            this.basePath = projectDir + File.separator + "data" + File.separator + "files";
        } else {
            this.basePath = basePath;
        }

        File baseDir = new File(this.basePath);
        if (!baseDir.exists()) {
            boolean created = baseDir.mkdirs();
            if (created) {
                LOGGER.info("Carpeta base creada: " + this.basePath);
            } else {
                LOGGER.warning("No se pudo crear la carpeta base: " + this.basePath);
            }
        }
    }

    private String getUserPath(Long userId) {
        return basePath + File.separator + userId;
    }

    private String getFilePath(Long userId, String fileId) {
        return getUserPath(userId) + File.separator + fileId;
    }

    @Override
    public boolean createDirectory(String ownerId, String path) throws RemoteException {
        return true;
    }

    @Override
    public boolean uploadFile(String fileId, byte[] content) throws RemoteException {
        LOGGER.info("Recibiendo archivo: " + fileId + ", tamaño: " + content.length + " bytes");

        Future<Boolean> future = threadPool.submit(() -> {
            try {
                Long userId = extractUserIdFromFileId(fileId);
                if (userId == null) return false;

                // Crear carpeta del usuario dentro de "data/files"
                File userDir = new File(getUserPath(userId));
                if (!userDir.exists() && !userDir.mkdirs()) {
                    LOGGER.warning("No se pudo crear carpeta: " + userDir.getAbsolutePath());
                    return false;
                }

                File targetFile = new File(getFilePath(userId, fileId));
                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(content);
                }

                LOGGER.info("Archivo guardado en: " + targetFile.getAbsolutePath());
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

    @Override
    public byte[] downloadFile(String fileId) {
        Future<byte[]> future = threadPool.submit(() -> {
            try {
                Long userId = extractUserIdFromFileId(fileId);
                if (userId == null) return null;

                File targetFile = new File(getFilePath(userId, fileId));
                if (!targetFile.exists()) {
                    LOGGER.warning("Archivo no encontrado: " + targetFile.getAbsolutePath());
                    return null;
                }

                return Files.readAllBytes(targetFile.toPath());

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

    @Override
    public List<byte[]> downloadFiles(List<String> filePaths) throws RemoteException {
        return List.of();
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
    public boolean deleteFile(String fileId) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteFiles(List<String> filePaths) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteDirectory(String directoryPath) throws RemoteException {
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

    private Long extractUserIdFromFileId(String fileId) {
        try {
            int firstDash = fileId.indexOf('-');
            if (firstDash <= 0) return null;
            return Long.parseLong(fileId.substring(0, firstDash));
        } catch (NumberFormatException e) {
            LOGGER.warning("Formato inválido de fileId: " + fileId);
            return null;
        }
    }
}
