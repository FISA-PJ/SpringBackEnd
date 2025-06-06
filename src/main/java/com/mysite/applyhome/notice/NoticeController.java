package com.mysite.applyhome.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import com.mysite.applyhome.housingSubscriptionEligibility.HousingSubscriptionEligibilityService;
import com.mysite.applyhome.user.SiteUserDetails;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Controller
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;
    private final HousingSubscriptionEligibilityService eligibilityService;

    @Value("${KAKAOMAP_API_KEY}")
    private String kakaoApiKey;

    // 공고 목록 페이지
    @GetMapping("/list")
    public String list(Model model, 
                      @RequestParam(value = "page", defaultValue = "0") int page,
                      @RequestParam(value = "keyword", required = false) String keyword) {
        Page<Notice> paging;
        if (keyword != null && !keyword.trim().isEmpty()) {
            paging = this.noticeService.searchNotices(keyword.trim(), PageRequest.of(page, 10));
        } else {
            paging = this.noticeService.getList(page);
        }
        model.addAttribute("paging", paging);
        model.addAttribute("kakaoApiKey", kakaoApiKey);
        model.addAttribute("keyword", keyword);
        return "notice_list";
    }

    // 공고 상세 페이지
    @GetMapping("/detail/{id}")
    public String detail(Model model, @PathVariable("id") Long id) {
        Notice notice = this.noticeService.getNotice(id);
        model.addAttribute("notice", notice);
        return "notice_detail";
    }

    // 제목으로 공고 검색
    @GetMapping("/search/title")
    public String searchByTitle(Model model, @RequestParam("keyword") String keyword) {
        List<Notice> notices = this.noticeService.searchByTitle(keyword);
        model.addAttribute("notices", notices);
        return "notice_list";
    }

    // 지역으로 공고 검색
    @GetMapping("/search/location")
    public String searchByLocation(Model model, @RequestParam("location") String location) {
        List<Notice> notices = this.noticeService.searchByLocation(location);
        model.addAttribute("notices", notices);
        return "notice_list";
    }

    // 접수중인 공고 조회
    @GetMapping("/active")
    public String activeNotices(Model model) {
        List<Notice> notices = this.noticeService.getActiveNotices();
        model.addAttribute("notices", notices);
        return "notice_list";
    }

    // 공고 상태로 검색
    @GetMapping("/status/{status}")
    public String noticesByStatus(Model model, @PathVariable("status") NoticeStatus status) {
        List<Notice> notices = this.noticeService.getNoticesByStatus(status);
        model.addAttribute("notices", notices);
        return "notice_list";
    }

    // 게시일 기준으로 공고 검색
    @GetMapping("/search/date")
    public String noticesByDateRange(Model model,
                                   @RequestParam("startDate") String startDate,
                                   @RequestParam("endDate") String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        List<Notice> notices = this.noticeService.getNoticesByDateRange(start, end);
        model.addAttribute("notices", notices);
        return "notice_list";
    }

    // 사용자 유형에 따른 공고 조회
    @GetMapping("/custom")
    public String customNotices(Model model, 
                              @RequestParam(value = "page", defaultValue = "0") int page,
                              @AuthenticationPrincipal SiteUserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/user/login";
        }

        String primeType = eligibilityService.getEligibilityPrimeType(userDetails.getUser());
        Page<Notice> paging = noticeService.getNoticesByPrimeType(primeType, page);
        
        model.addAttribute("paging", paging);
        model.addAttribute("kakaoApiKey", kakaoApiKey);
        return "notice_list";
    }

    // 맞춤 공고 데이터를 JSON으로 반환
    @GetMapping("/api/custom")
    @ResponseBody
    public Page<Notice> getCustomNotices(@RequestParam(value = "page", defaultValue = "0") int page,
                                        @AuthenticationPrincipal SiteUserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        String primeType = eligibilityService.getEligibilityPrimeType(userDetails.getUser());
        return noticeService.getNoticesByPrimeType(primeType, page);
    }

    // 필터링된 공고 데이터를 JSON으로 반환
    @GetMapping("/api/filter")
    @ResponseBody
    public Page<Notice> getFilteredNotices(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "price", required = false) String price,
            @RequestParam(value = "moveInDate", required = false) String moveInDate) {
        
        return noticeService.getFilteredNotices(page, region, area, price, moveInDate);
    }
} 