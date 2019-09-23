/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bloc97.anime4k;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author bloc97
 */
public class Main {

    static ImageKernel kernel = new ImageKernel();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Map<Image, String> namesOfImages = new HashMap<>();
        List<Callable<Image>> tasks = new LinkedList<>();
        Path inputDir;
        Path outputDir;

        if (args.length < 2) {
            System.out.println("Error: Please specify input and output directories.");
            System.exit(1);
        }

        inputDir = Paths.get(args[0]);
        outputDir = Paths.get(args[1]);

        if (Files.exists(inputDir) && Files.isDirectory(inputDir)) {
            Files.list(inputDir).forEach(path -> {
                if (Files.isDirectory(path))
                    return;

                BufferedImage image;
                try {
                    float scale =             args.length >= 3 ? Float.parseFloat(args[2]) : 2f;
                    float pushStrength =      args.length >= 4 ? Float.parseFloat(args[3]) : scale / 6f;
                    float pushGradStrength =  args.length >= 5 ? Float.parseFloat(args[4]) : scale / 2f;
                    image = ImageIO.read(path.toFile());

                    tasks.add(() -> {
                        Image img = upscale(image, scale, pushGradStrength, pushStrength);
                        namesOfImages.put(img, path.getFileName().toString());

                        return img;
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            if (!Files.exists(outputDir))
                Files.createDirectories(outputDir);

            try {
                Future<Image> imageFuture;
                do {
                    imageFuture = executor.submit(tasks.remove(0));
                    Image upscaledImg = imageFuture.get();
                    String imgName = namesOfImages.remove(upscaledImg);
                    Path newImgPath = Paths.get(outputDir.toAbsolutePath().toString(), imgName);

                    ImageIO.write((BufferedImage) upscaledImg, "png", newImgPath.toFile());
                } while (!tasks.isEmpty());
            } catch (InterruptedException | ExecutionException err) {
                err.printStackTrace();
                System.exit(2);
            } catch (IOException err) {
                err.printStackTrace();
                System.err.println("Error writing to disk");
                System.exit(3);
            } finally {
                executor.shutdown();
            }
        } else {
            System.err.println("Input and output files must be a directory");
            System.exit(4);
        }
    }

    static Image upscale(BufferedImage img, float scale, float pushGradStrength, float pushStrength) {
        img = copyType(img);
        img = scale(img, (int) (img.getWidth() * scale), (int) (img.getHeight() * scale));

        kernel.setPushStrength(pushStrength);
        kernel.setPushGradStrength(pushGradStrength);
        kernel.setBufferedImage(img);
        kernel.process();
        kernel.updateBufferedImage();

        return img;
    }

    static BufferedImage copyType(BufferedImage bi) {
        BufferedImage newImage = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
        newImage.getGraphics().drawImage(bi, 0, 0, null);

        return newImage;
    }

    static BufferedImage scale(BufferedImage bi, int width, int height) {
        BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) newImage.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(bi, 0, 0, width, height, null);

        return newImage;
    }

}
