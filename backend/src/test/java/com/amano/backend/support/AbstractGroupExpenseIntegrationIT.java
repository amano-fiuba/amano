package com.amano.backend.support;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.amano.backend.groups.ActivityEventService;

/**
 * Shared integration-test base for shared/group expense ITs that spy on
 * {@link ActivityEventService}. The BCRA client mock comes from
 * {@link AbstractIntegrationIT} so every IT is hermetic.
 */
public abstract class AbstractGroupExpenseIntegrationIT extends AbstractIntegrationIT {

    @MockitoSpyBean
    protected ActivityEventService activityEventService;
}
