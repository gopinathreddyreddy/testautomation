package com.service.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gopinathreddy.p on 6/11/2017.
 */
public class FileUtils {
    private static final int BUFFER_SIZE = 1000;
    private static final String ENCODING = "UTF-8";
    private Logger log = LoggerFactory.getLogger(FileUtils.class);

    private interface Filter {
        boolean accept(String t);
    }

    public List<Path> find(Path path, final String endsWith) {
        List<Path> paths = new ArrayList();
        find(path, new Filter() {
            public boolean accept(String t) {
                return t.endsWith(endsWith);
            }
        }, paths);

        log.debug("Paths: {}", paths);
        return paths;

    }

    public String read(Path path) throws IOException {
        File file = path.toFile();
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[BUFFER_SIZE];
            int n = -1;
            while ((n = bis.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, n, ENCODING));
            }
            return sb.toString();
        } finally {
            if (bis != null) {
                bis.close();
            }
        }

    }

    private void find(Path path, final Filter filter, List<Path> paths) {
        File file = path.toFile();
        if (file.isDirectory()) {
            File[] acceptedFiles = file.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isFile();
                }
            });
            for (int i = 0; i < acceptedFiles.length; i++) {
                find(acceptedFiles[i].toPath(), filter, paths);
            }
            File[] dirFiles = file.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory();
                }
            });
            for (int i = 0; i < dirFiles.length; i++) {
                find(dirFiles[i].toPath(), filter, paths);
            }
        } else if (filter.accept(file.getName())) {
            log.debug("adds {}", file.getName());
            paths.add(path);
        }
    }
}