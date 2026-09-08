package com.lnxx.hr.rag;

import com.lnxx.hr.security.AuthPrincipal;
import com.lnxx.hr.security.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class KnowledgeServiceTest {
    @Autowired KnowledgeService service;

    @Test void departmentKnowledgeIsVisibleToMember() {
        var user = new AuthPrincipal("employee", "E1001", UserRole.EMPLOYEE);
        assertThat(service.search("弹性上班", user, "研发部"))
                .extracting(chunk -> chunk.documentId()).contains("RD-001");
    }

    @Test void hrOnlyKnowledgeIsHiddenFromEmployee() {
        var user = new AuthPrincipal("employee", "E1001", UserRole.EMPLOYEE);
        assertThat(service.search("人事审批操作规范", user, "研发部"))
                .noneMatch(chunk -> chunk.documentId().equals("HR-001"));
    }
}
