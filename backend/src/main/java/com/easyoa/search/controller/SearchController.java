package com.easyoa.search.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.search.application.SearchService;
import com.easyoa.search.dto.SearchResponse;

/**
 * 全局搜索接口（顶部搜索 / 命令面板共用）。
 *
 * <p>结果按类别分组（项目 / 任务 / 成员 / 审批），每组最多 5 条，全部携带深链。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam(name = "q", required = false) String query) {
        return ApiResponse.ok(searchService.search(query));
    }
}