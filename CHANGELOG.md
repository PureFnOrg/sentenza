# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.2.0] - 2017-09-08
### Changed
- Signature of `sentenza.api/kickoff` fn to dispatch to two versions of the api,
one the existing component fn and new light-weight one.
### Added
- `sentenza.api/kickoff-pipeline` - renamed existing `kickoff` fn to this one.
- `sentenza.api/kickoff-flow` - lightweight version of kickoff which does not
require a Pipeline and is configured by function arguments.
### Removed
- `extend-protocol` on Object for Pipeline protocol

[Unreleased]: https://github.com/theladders/sentenza/compare/0.2.0...HEAD
[0.2.0]: https://github.com/theladders/sentenza/compare/0.1.6...0.2.0

