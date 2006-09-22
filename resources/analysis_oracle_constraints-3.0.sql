

ALTER TABLE GPPORTAL.TASK_MASTER
 ADD FOREIGN KEY (ACCESS_ID) 
 REFERENCES GPPORTAL.TASK_ACCESS (ACCESS_ID);



ALTER TABLE GPPORTAL.ANALYSIS_JOB
 ADD FOREIGN KEY (STATUS_ID) 
 REFERENCES GPPORTAL.JOB_STATUS (STATUS_ID);
 
ALTER TABLE GPPORTAL.ANALYSIS_JOB
 ADD FOREIGN KEY (TASK_ID) 
 REFERENCES GPPORTAL.TASK_MASTER (TASK_ID);
 
ALTER TABLE GPPORTAL.ANALYSIS_JOB
 ADD FOREIGN KEY (ACCESS_ID) 
 REFERENCES GPPORTAL.TASK_ACCESS (ACCESS_ID);
 
ALTER TABLE GPPORTAL.GP_USER_PROP
 ADD FOREIGN KEY (GP_USER_ID) 
 REFERENCES GPPORTAL.GP_USER (GP_USER_ID);










