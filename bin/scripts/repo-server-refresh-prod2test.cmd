@echo off
sc \\s0020269 stop owldbTest
mkdir \\s0020269\c$\owldbTest\data-backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
copy \\s0020269\c$\owldbTest\data \\s0020269\c$\owldbTest\data-backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
del \\s0020269\c$\owldbTest\data\* /Q
sc \\s0144737 stop ontologyServer
copy \\s0144737\c$\ontologyServer\data \\s0020269\c$\owldbTest\data
sc \\s0144737 start ontologyServer
sc \\s0020269 start owldbTest