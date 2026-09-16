package com.lczz.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "lczz.file.local-root=target/test-product-image-storage")
class ProductImageVariantIntegrationTests {
    private static final Path STORAGE_ROOT = Path.of("target/test-product-image-storage");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    private long categoryId;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM material_self_order_item");
        jdbcTemplate.update("DELETE FROM material_self_order");
        jdbcTemplate.update("DELETE FROM installer_cart_item");
        jdbcTemplate.update("DELETE FROM product_sku_spec_value");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product_spec_value");
        jdbcTemplate.update("DELETE FROM product_spec_dimension");
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM file_asset");
        cleanStorage();
        jdbcTemplate.update("INSERT INTO product_category(category_code, category_name, enabled, deleted) "
                + "VALUES ('image-test', '图片测试', TRUE, FALSE)");
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_category WHERE category_code='image-test'", Long.class);
    }

    @Test
    void publicProductImageUsesStableVersionedUrlsCacheHeadersAndConditionalGet() throws Exception {
        byte[] original = photo(1800, 1200, 11);
        long fileId = storeFile("cover.jpg", "image/jpeg", original);
        jdbcTemplate.update("UPDATE file_asset SET sha256=NULL WHERE id=?", fileId);
        long productId = createProduct("IMG-CACHE", fileId, true);

        JsonNode firstList = responseJson(get("/api/v1/consumables/list"));
        String cardUrl = firstList.at("/data/list/0/image").asText();
        assertThat(cardUrl).contains("/public/product-images/" + fileId + "/card/");
        assertThat(cardUrl).doesNotContain("legacy-");
        assertThat(jdbcTemplate.queryForObject("SELECT sha256 FROM file_asset WHERE id=?", String.class, fileId))
                .isEqualTo(sha256(original));
        assertThat(firstList.at("/data/list/0/thumbnail").asText()).isEqualTo(cardUrl);
        JsonNode secondList = responseJson(get("/api/v1/consumables/list"));
        assertThat(secondList.at("/data/list/0/image").asText()).isEqualTo(cardUrl);

        JsonNode detail = responseJson(get("/api/v1/consumables/detail/{id}", productId));
        assertThat(detail.at("/data/displayImage").asText()).contains("/display/");
        assertThat(detail.at("/data/originalImage").asText()).contains("/original/");
        assertThat(detail.at("/data/images/0/cardUrl").asText()).contains("/card/");

        MvcResult card = mockMvc.perform(get(cardUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().exists("Content-Length"))
                .andExpect(header().exists("ETag"))
                .andReturn();
        byte[] cardBytes = card.getResponse().getContentAsByteArray();
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(cardBytes));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(640);
        assertThat(cardBytes.length).isLessThanOrEqualTo(300 * 1024);
        String etag = card.getResponse().getHeader("ETag");
        mockMvc.perform(get(cardUrl).header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));

        long replacementId = storeFile("replacement.jpg", "image/jpeg", photo(1200, 900, 12));
        jdbcTemplate.update("UPDATE product SET cover_file_id=? WHERE id=?", replacementId, productId);
        String replacementUrl = responseJson(get("/api/v1/consumables/list"))
                .at("/data/list/0/image").asText();
        assertThat(replacementUrl).isNotEqualTo(cardUrl).contains("/" + replacementId + "/card/");

        jdbcTemplate.update("UPDATE product SET enabled=FALSE WHERE id=?", productId);
        mockMvc.perform(get(replacementUrl)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_IMAGE_NOT_FOUND"));
    }

    @Test
    void cardAndDisplayBudgetsHoldForThreeLargeProductImages() throws Exception {
        int[][] dimensions = {{1800, 1200}, {1700, 1700}, {2400, 900}};
        for (int index = 0; index < dimensions.length; index++) {
            byte[] original = photo(dimensions[index][0], dimensions[index][1], 30 + index);
            long fileId = storeFile("sample-" + index + ".jpg", "image/jpeg", original);
            long productId = createProduct("IMG-SAMPLE-" + index, fileId, true);
            JsonNode detail = responseJson(get("/api/v1/consumables/detail/{id}", productId));
            assertVariant(detail.at("/data/imageVariants/cardUrl").asText(), 640, 300 * 1024);
            assertVariant(detail.at("/data/imageVariants/displayUrl").asText(), 1600, 1024 * 1024);
        }
    }

    @Test
    void unboundFilesStayPrivateAndUnsupportedDerivativeFallsBackToOriginal() throws Exception {
        byte[] privateImage = photo(800, 500, 41);
        long privateId = storeFile("private.jpg", "image/jpeg", privateImage);
        String privateVersion = sha256(privateImage);
        jdbcTemplate.update("INSERT INTO business_file_relation(business_type, business_id, usage_type, file_id) "
                + "VALUES ('ORDER', 991, 'ATTACHMENT', ?)", privateId);
        jdbcTemplate.update("INSERT INTO business_file_relation(business_type, business_id, usage_type, file_id) "
                + "VALUES ('PROGRESS', 992, 'PROGRESS', ?)", privateId);
        jdbcTemplate.update("INSERT INTO business_file_relation(business_type, business_id, usage_type, file_id) "
                + "VALUES ('REVIEW', 993, 'REVIEW', ?)", privateId);
        mockMvc.perform(get("/api/v1/public/product-images/{id}/card/{version}", privateId, privateVersion))
                .andExpect(status().isNotFound());

        byte[] webp = new byte[] {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0, 0, 0};
        long webpId = storeFile("legacy.webp", "image/webp", webp);
        long productId = createProduct("IMG-WEBP", webpId, true);
        String cardUrl = responseJson(get("/api/v1/consumables/detail/{id}", productId))
                .at("/data/imageVariants/cardUrl").asText();
        mockMvc.perform(get(cardUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(webp));
    }

    private void assertVariant(String url, int maxEdge, int maxBytes) throws Exception {
        MvcResult response = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        byte[] bytes = response.getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertThat(image).isNotNull();
        assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(maxEdge);
        assertThat(bytes.length).isLessThanOrEqualTo(maxBytes);
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private long createProduct(String code, long coverFileId, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO product(product_code, product_name, category_id, unit, cover_file_id, enabled, deleted)
                VALUES (?, ?, ?, '件', ?, ?, FALSE)
                """, code, code, categoryId, coverFileId, enabled);
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE product_code=?", Long.class, code);
    }

    private long storeFile(String name, String mimeType, byte[] content) throws Exception {
        String hash = sha256(content);
        String objectKey = "tests/" + hash + "-" + name;
        Path target = STORAGE_ROOT.resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        jdbcTemplate.update("""
                INSERT INTO file_asset(storage_type, object_key, original_name, mime_type, file_size, sha256,
                                       access_url, deleted)
                VALUES ('LOCAL', ?, ?, ?, ?, ?, '', FALSE)
                """, objectKey, name, mimeType, content.length, hash);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_asset", Long.class);
    }

    private byte[] photo(int width, int height, int seed) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int noise = (x * 31 + y * 17 + seed * 53 + (x * y) % 251) & 0xff;
                int red = (noise + x * 255 / width) & 0xff;
                int green = (noise / 2 + y * 255 / height) & 0xff;
                int blue = (noise + (x + y) * 127 / (width + height)) & 0xff;
                image.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.drawString("LCZZ product sample " + seed, 32, 48);
        graphics.dispose();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.96F);
            writer.write(null, new IIOImage(image, null, null), params);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private void cleanStorage() throws Exception {
        Path root = STORAGE_ROOT.toAbsolutePath().normalize();
        Path target = Path.of("target").toAbsolutePath().normalize();
        if (!root.startsWith(target) || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(root)).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
    }
}
