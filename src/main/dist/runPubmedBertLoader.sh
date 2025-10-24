
#!/bin/bash
# Set the JAVA_HOME environment variable to the path of your Java installation

# Run the Java class
#java -classpath /home/rgddata/pipelines/text-mining-tools/build/libs/text-mining-tools.jar:/home/rgddata/pipelines/text-mining-tools/build/libs/dev-pubmedbert-textminingtools.jar edu.mcw.rgd.nlp.PubMedBertAnnotator "/data/ai"
# Run the Java class
/usr/lib/jvm/java-17/bin/java -Dspring.config=/home/rgdpub/properties/default_db2.xml -classpath /home/rgdpub/pipelines/text-mining-tools/lib/*:/home/rgdpub/pipelines/text-mining-tools/build/libs/text-mining-tools.jar edu.mcw.rgd.nlp.PubMedBertLoader "$1" "$2"