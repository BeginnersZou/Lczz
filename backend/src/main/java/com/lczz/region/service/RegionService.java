package com.lczz.region.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class RegionService {
    private static final String DATA_PATH = "regions/china-regions.json";

    private final List<RegionNode> tree;

    public RegionService(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(DATA_PATH).getInputStream()) {
            List<RegionNode> loaded = objectMapper.readValue(input, new TypeReference<>() { });
            if (loaded.size() < 31 || loaded.stream().anyMatch(node -> node.children().isEmpty())) {
                throw new IllegalStateException("Administrative region data is incomplete");
            }
            this.tree = List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load administrative region data", exception);
        }
    }

    public List<RegionNode> tree() {
        return tree;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegionNode(String code, String name, List<RegionNode> children) {
        public RegionNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
