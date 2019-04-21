# timecontrol
Time crontol sample - Ejemplo de control horario 

1) Upload the control-time.html to /okm:templates OpenKM 

2) Copy the UUID of the control-time ( UUID available from profile tab ), then go to Administration > Database query and execute the query below, replacing the UUID by your own
```
INSERT INTO OKM_CONFIG (CFG_KEY, CFG_TYPE, CFG_VALUE) VALUES ('time.control.template.uuid','string','YOUR_CONTROL_TIME_FILE_UUID'); 
```

3) Create a folder named /okm:root/control-time 

4) Copy the UUID of the control-time folder ( UUID available from profile tab ), then go to Administration > Database query and execute the query below, replacing the UUID by your own 
```
INSERT INTO OKM_CONFIG (CFG_KEY, CFG_TYPE, CFG_VALUE) VALUES ('time.control.folder.uuid','string','YOUR_CONTROL_TIME_FOLDER_UUI');
```

5) Set the date format. Go to Administration > Database query and execute the query below
```
INSERT INTO OKM_CONFIG (CFG_KEY, CFG_TYPE, CFG_VALUE) VALUES ('time.control.date.format','string','dd/MM/yyyy');
```

6) Download the latest Time-control-1.0.jar from [releases github section](https://github.com/openkm/timecontrol/releases) and copy into your $TOMCAT_HOME/plugins folder

7) Restart OpenKM

8) Register Automation Rule for the event ***"User login"*** in ***post*** stage with a action ***"TimeControl"***.

9) Register Automation Rule for the event ***"User logout"*** in ***pre*** stage with a action ***"TimeControl"***.


