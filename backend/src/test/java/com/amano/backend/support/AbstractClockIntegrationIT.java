package com.amano.backend.support;

import java.time.Clock;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.amano.backend.groupexpense.reminder.ReminderMailService;

/**
 * Shared integration-test base for ITs that control time via a mocked
 * {@link Clock} and/or spy on {@link ReminderMailService}.
 */
public abstract class AbstractClockIntegrationIT extends AbstractIntegrationIT {

    @MockitoBean
    protected Clock clock;

    @MockitoSpyBean
    protected ReminderMailService mailService;
}
