package com.guardbench.evaluation.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.guardbench.evaluation.application.ExecuteAiServiceAssertionService;
import com.guardbench.evaluation.domain.AssertionResult;
import com.guardbench.evaluation.domain.AssertionStatus;
import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.evaluation.presentation.dto.AiServiceAssertionExecuteReq;

class AiServiceAssertionControllerTest {

    @Test
    void executesAssertionThroughDeployableControllerEntryPoint() {
        ExecuteAiServiceAssertionService service = mock(ExecuteAiServiceAssertionService.class);
        when(service.execute(anyString(), anyList())).thenReturn(List.of(
                new ExecuteAiServiceAssertionService.Result(
                        "허용 입력",
                        EvaluationAction.ALLOW,
                        EvaluationAction.ALLOW,
                        new AssertionResult(AssertionStatus.PASS)
                )
        ));
        AiServiceAssertionController controller = new AiServiceAssertionController(service);
        var request = new AiServiceAssertionExecuteReq(List.of(
                new AiServiceAssertionExecuteReq.CaseReq("허용 입력", EvaluationAction.ALLOW)
        ));

        var response = controller.execute(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().results()).hasSize(1);
        assertThat(response.getBody().data().results().getFirst().assertionStatus()).isEqualTo(AssertionStatus.PASS);
        verify(service).execute(anyString(), anyList());
    }
}
