package com.quanxiaoha.ai.robot.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * @Author: 犬小哈
 * @Date: 2025/6/24 16:39
 * @Version: v1.0.0
 * @Description: 演员 - 电影集合
 **/
@JsonPropertyOrder({"actor", "movies"})
public record ActorFilmography(String actor, List<String> movies) {
}
