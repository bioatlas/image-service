### image-service   [![Build Status](https://travis-ci.org/bioatlas/image-service.svg?branch=master)](https://travis-ci.org/bioatlas/image-service)

This Grails application provides the webservices and backend for the storage of all images in the Atlas.
It includes:

* Support for large images
* Extensible key/value pair storage for image metadata
* Support for subimaging and maintaining the relationships between parent and child images
* Exif extraction
* Tile view for large images compatible with GIS Javascript clients such as LeafletJS, OpenLayers and Google Maps
* Webservices for image upload
* Generate of derivative images for thumbnail presentation
* Tagging support
* Creation of albums of images and bulk tagging
* Administrator console for image management

There are other related repositories to this one:
* [images-client-plugin](https://github.com/AtlasOfLivingAustralia/images-client-plugin) - a grails plugin to provide a Javascript based viewer to be used in other applications requiring a image viewer. This viewer is based on LeafletJS.
* [image-tiling-agent](https://github.com/AtlasOfLivingAustralia/image-tiling-agent) - a utility to run tiling jobs for the image-service. This is intended to used on multiple machine as tiling is CPU intensive and best parallelised.
* [image-loader](https://github.com/AtlasOfLivingAustralia/image-loader) - utility for bulk loading images into the image-service.

## Architecture

* Grails 2.5.6 web application ran in the tomcat 7 or above
* Oracle Java 8
* Postgres database (9.3 or above)

## Installation

There are ansible scripts for this applications (and other ALA tools) in the [ala-install](https://github.com/AtlasOfLivingAustralia/ala-install) project. The ansible playbook for the image-service is [here](https://github.com/AtlasOfLivingAustralia/ala-install/blob/master/ansible/image-service.yml)

You can also run this application locally by following the instructions on its [wiki page](https://github.com/AtlasOfLivingAustralia/image-service/wiki)
