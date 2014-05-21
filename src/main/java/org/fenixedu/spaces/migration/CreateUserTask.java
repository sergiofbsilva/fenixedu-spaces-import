package org.fenixedu.spaces.migration;

import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class CreateUserTask extends CustomTask {

    private static final Logger logger = LoggerFactory.getLogger(CreateUserTask.class);

    @Override
    public void runTask() throws Exception {
        User user = new User("ypto");
    }

}