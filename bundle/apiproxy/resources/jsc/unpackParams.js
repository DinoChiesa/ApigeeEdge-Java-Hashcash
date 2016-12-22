// unpackParams.js
// ------------------------------------------------------------------
//
// Set a context variable for each property in a flat JSON hash.
//
// created: Thu Dec 22 15:06:33 2016
// last saved: <2016-December-22 15:22:05>

var c = JSON.parse(context.getVariable('request.content'));
var prefix = 'parsedInput.';
Object.keys(c).forEach(function(key){
  // should check typeof here, but this is just for a demo
  context.setVariable(prefix + key, c[key] + '');
});
