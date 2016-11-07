sc \\s0144654 stop cirmservices
ping -n 5 127.0.0.1>null
del \\s0144654\c$\cirmservices\dbConf\* /Q
sc \\s0144654 start cirmservices
ping -n 5 127.0.0.1>null