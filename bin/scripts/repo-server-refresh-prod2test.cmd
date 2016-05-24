@
sc \\s0020269 stop owldbTest
mkdir \\s0020269\c$\owldbTest\data-backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
copy \\s0020269\c$\owldbTest\data \\s0020269\c$\owldbTest\data-backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
del \\s0020269\c$\owldbTest\data\* /Q
sc \\s0141669 stop owldbserver
copy \\s0141669\c$\owldb\data \\s0020269\c$\owldbTest\data
sc \\s0141669 start owldbserver
sc \\s0020269 start owldbTest