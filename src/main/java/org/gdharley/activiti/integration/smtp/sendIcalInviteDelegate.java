package org.gdharley.activiti.integration.general;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.persistence.entity.JobEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by gharley on 5/2/17.
 */
public class InsertTimerDelegate implements JavaDelegate {
    private static final Logger logger = LoggerFactory.getLogger(InsertTimerDelegate.class);

    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Started call delegate");
        JobEntityManager jem = Context.getCommandContext().getJobEntityManager();
    }
}
