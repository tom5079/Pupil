#!/bin/bash

TAGS_PYTHON=
TAGS_FOLDER=

$TAGS_PYTHON -m pip install -r $TAGS_FOLDER/scripts/requirements.txt > NUL

tags=$($TAGS_PYTHON $TAGS_FOLDER/scripts/tags.py)

for file in "$(ls $TAGS_FOLDER/*.json)"
do
    echo "$tags" | $TAGS_PYTHON $TAGS_FOLDER/scripts/update.py $file
done