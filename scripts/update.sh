#!/bin/bash

$TAGS_PYTHON -m pip install -r $TAGS_FOLDER/scripts/requirements.txt > /dev/null 2>&1

tags=$($TAGS_PYTHON $TAGS_FOLDER/scripts/tags.py)

for file in $TAGS_FOLDER/*.json
do
    echo "$tags" | $TAGS_PYTHON $TAGS_FOLDER/scripts/update.py $file
done
