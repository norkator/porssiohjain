package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final AccountRepository accountRepository;

    public void createSite(
            Long accountId, String name, SiteType type, Boolean enabled
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        SiteEntity site = SiteEntity.builder()
                .name(name)
                .type(type)
                .enabled(enabled)
                .account(account)
                .build();
        siteRepository.save(site);
    }

    public List<SiteResponse> getAllSites(Long accountId) {
        return siteRepository.findByAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SiteResponse toResponse(SiteEntity site) {
        return SiteResponse.builder()
                .id(site.getId())
                .name(site.getName())
                .type(site.getType())
                .enabled(site.getEnabled())
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .build();
    }

}