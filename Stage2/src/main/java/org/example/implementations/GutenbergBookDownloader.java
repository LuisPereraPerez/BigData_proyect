package org.example.implementations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import org.example.interfaces.BookDownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class GutenbergBookDownloader implements BookDownloader {
    private final String saveDir;

    // Constructor que recibe el directorio de guardado
    public GutenbergBookDownloader(String saveDir) {
        this.saveDir = saveDir;
    }

    @Override
    public void downloadBook(int bookId) throws IOException {
        String url = "https://www.gutenberg.org/ebooks/" + bookId;
        Document doc = Jsoup.connect(url).timeout(60000).get();

        // Buscar el enlace al archivo de texto (Plain Text UTF-8)
        String textLink = getTextLink(doc);

        if (textLink != null) {
            // Configuración de `HttpClient` con tiempos de espera de conexión y de socket
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                            .setConnectTimeout(Timeout.ofMinutes(10)) // Tiempo de espera para establecer la conexión
                            .setResponseTimeout(Timeout.ofMinutes(10)) // Tiempo de espera para la respuesta
                            .build())
                    .build()) {

                HttpGet httpGet = new HttpGet(URI.create(textLink));
                String bookFileName = saveDir + "/" + bookId + ".txt";
                File file = new File(bookFileName);

                // Crear el directorio de destino si no existe
                File dir = new File(saveDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int status = response.getCode();
                    if (status == HttpStatus.SC_OK) {
                        // Descarga el contenido y escribe en el archivo en fragmentos
                        try (InputStream in = response.getEntity().getContent();
                             FileOutputStream out = new FileOutputStream(file)) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }

                            System.out.println("Libro con ID " + bookId + " descargado exitosamente.");
                        }
                    } else {
                        throw new HttpResponseException(status, "No se pudo descargar el libro.");
                    }
                }
            }
        } else {
            System.out.println("El libro con ID " + bookId + " no tiene un archivo de texto disponible.");
        }
    }

    // Método para obtener el enlace al archivo de texto en formato UTF-8
    private static String getTextLink(Document doc) {
        Element link = doc.select("a[href]").stream()
                .filter(a -> a.text().equals("Plain Text UTF-8")) // Buscar enlace al texto
                .findFirst()
                .orElse(null);
        if (link != null) {
            return "https://www.gutenberg.org" + link.attr("href"); // Construir URL completa
        }
        return null;
    }
}
