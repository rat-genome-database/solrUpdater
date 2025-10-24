#!/bin/bash

curl -X POST -H 'Content-Type: application/json' --data-binary '{"delete":{"query":"*:*" }}' http://hansen.rgd.mcw.edu:8983/solr/$1/update