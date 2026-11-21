package com.just.critter.controller.archive;

import com.just.critter.domain.enums.Season;
import com.just.critter.service.archive.BiodiversityService;
import com.just.critter.service.archive.CreatedOccurrence;
import com.just.critter.service.archive.OccurrenceFilter;
import com.just.critter.service.archive.OccurrenceView;
import com.just.critter.service.archive.PointView;
import com.just.critter.service.archive.SpeciesFilter;
import com.just.critter.service.archive.SpeciesView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.security.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物种名录、点位字典与出现记录（03 §6.2 #20—#25 的只读部分 + 出现登记）。
 * 物种与点位的新增、修改为 P1，尚未开放。
 */
@RestController
@RequestMapping("/api/v1")
public class BiodiversityController {

    private final BiodiversityService service;

    public BiodiversityController(BiodiversityService service) {
        this.service = service;
    }

    @GetMapping("/species")
    public Result<List<SpeciesView>> species(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String category) {
        return Result.ok(service.listSpecies(new SpeciesFilter(keyword, category)));
    }

    @GetMapping("/points")
    public Result<List<PointView>> points() {
        return Result.ok(service.listPoints());
    }

    @GetMapping("/occurrences")
    public Result<PageResult<OccurrenceView>> occurrences(
            @RequestParam(required = false) Long speciesId,
            @RequestParam(required = false) Long pointId,
            @RequestParam(required = false) String season,
            PageQuery pageQuery) {
        Season parsed = null;
        if (season != null && !season.isBlank()) {
            try {
                parsed = Season.valueOf(season.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(30303, "季节非法：" + season);
            }
        }
        return Result.ok(service.searchOccurrences(
                new OccurrenceFilter(speciesId, pointId, parsed, null), pageQuery));
    }

    @PostMapping("/occurrences")
    public Result<CreatedOccurrence> createOccurrence(@RequestBody CreateOccurrenceRequest request) {
        return Result.ok(service.createOccurrence(request.toCommand(), AuthContext.require().id()));
    }
}
