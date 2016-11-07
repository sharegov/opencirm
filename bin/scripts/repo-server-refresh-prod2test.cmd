@echo off
sc \\s0020269 stop owldbTest
ping -n 5 127.0.0.1>null
mkdir \\s0020269\c$\owldbTest\data-backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
copy \\s0020269\c$\owldbTest\data \\s0020269\c$\owldbTest\data-backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
del \\s0020269\c$\owldbTest\data\* /Q
sc \\s0144737 stop ontologyServer
ping -n 5 127.0.0.1>null
copy \\s0144737\c$\ontologyServer\data \\s0020269\c$\owldbTest\data
sc \\s0144737 start ontologyServer
ping -n 5 127.0.0.1>null
sc \\s0020269 start owldbTest
ping -n 5 127.0.0.1>null