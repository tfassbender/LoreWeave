package com.tfassbender.loreweave.rest.dto;

import java.util.List;

/** Related-graph response. */
public record RelatedResponse(String node, List<RelatedNode> related) {

    public record RelatedNode(String path, String title, String type, String relation) {}
}
