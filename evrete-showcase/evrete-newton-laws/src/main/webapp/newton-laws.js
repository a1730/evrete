let newtonLaws = (function () {
    if (!('WebSocket' in window || 'MozWebSocket' in window)) {
        throw new Error('WebSocket is not supported');
    }

    const SOCKET = _createWebSocket();
    const SVG_NS = "http://www.w3.org/2000/svg";
    const SVG = document.getElementById('plot');
    const DEFAULT_VIEW_WIDTH = Number.parseInt(SVG.getAttribute('width'));
    const DEFAULT_VIEW_HEIGHT = Number.parseInt(SVG.getAttribute('height'));
    const SVG_CANVAS = document.getElementById('svg-background');

    const ELEMENT_LOG = $('#logs');
    const MODE_RUNNING = 'RUNNNING';
    const MODE_STOPPED = 'STOPPED';

    $('#gravity').val(50);

    const CONFIGURATION = {};
    const UI_ELEMENTS = {};

    function _startEmulation() {
        $('.clearable').empty();
        let msg = {
            'type': 'START'
        }
        SOCKET.send(JSON.stringify(msg, null, 2));
        $('#start-button').prop('disabled', true);
    }

    function _pauseEmulation() {
        let m = {
            'type': 'PAUSE'
        };
        SOCKET.send(JSON.stringify(m));
        $('#start-button').prop('disabled', true);
    }

    function _sendStop() {
        let m = {
            'type': 'STOP'
        };
        _clearAll();
        SOCKET.send(JSON.stringify(m));
    }

    function onUiReset() {
    }

    function _clearAll() {
        $('.clearable').empty();
        let particles = CONFIGURATION.particles;
        for (let id in particles) {
            if (particles.hasOwnProperty(id)) {
                let o = particles[id];
                if (o) {
                    deleteParticle(o);
                    delete particles[id];
                }
            }
        }
        for (let id in UI_ELEMENTS) {
            if (UI_ELEMENTS.hasOwnProperty(id)) {
                delete UI_ELEMENTS[id];
            }
        }
        CONFIGURATION.counter = 0;
        CONFIGURATION.velocityMode = false;
    }

    function onMessage(evt) {
        let msg = JSON.parse(evt.data);
        console.log('Message', msg)
        switch (msg['type']) {
            case 'CONFIG':
                //TODO !!!
                //EDITOR_RULES.setValue(msg.rules, -1);

                break;
            case 'ERROR':
                ELEMENT_LOG.append('<li class="ERROR">Error: <pre>' + msg.text + '</pre></li>');
                break;
            case 'LOG':
                ELEMENT_LOG.append('<li>' + msg.text + '</li>');
                break;
            case 'REPORT':
                let updated = JSON.parse(msg.text);
                let particles = CONFIGURATION.particles;

                let deletions = false;
                for (let i in particles) {
                    if (particles.hasOwnProperty(i)) {
                        let existing = particles[i];
                        console.log("Particle", existing)
                        let particle = updated[existing.id];
                        if (particle) {
                            _paintParticle(particle);
                            particles[i] = particle;
                        } else {
                            // No such particle anymore
                            console.log('Not found');
                            deleteParticle(existing);
                            delete particles[i];
                            deletions = true;
                            delete UI_ELEMENTS[i];
                        }
                    }
                }

                if (deletions) {
                    // Update colors on the right
                    for (let i in particles) {
                        if (particles.hasOwnProperty(i)) {
                            $('#object-controls li[data-particle-id=' + i + ']')
                                .css('border-left-color', toStringColor(particles[i].color));
                        }
                    }
                }

                break;
            case 'STOPPED':
                if (CONFIGURATION.mode !== MODE_EDIT) {
                    CONFIGURATION.mode = MODE_STOPPED;
                }
                break;
            case 'STARTED':
                CONFIGURATION.mode = MODE_RUNNING;
                break;
            case 'PAUSED':
                CONFIGURATION.mode = MODE_PAUSED;
                break;
        }
    }

    function deleteParticle(particle) {
        let id = particle.id.toString();
        $('[data-particle-id=' + id + ']').remove();
        if (id === CONFIGURATION.viewerParticleId) {
            $('#object-viewer').addClass('invisible')
        }

    }

    function _paintParticle(particle) {
        if (!particle) return;
        const id = particle.id.toString();


        let particleUI = UI_ELEMENTS[id];
        const color = toStringColor(particle.color);
        const radius = Math.pow(particle.mass, 1.0 / 3);

        let ellipse;
        let velocity;
        let acceleration;
        if (!particleUI) {
            // Position
            ellipse = document.createElementNS(SVG_NS, 'ellipse');
            ellipse.setAttribute('data-particle-id', id);

            SVG.appendChild(ellipse);
            ellipse.onclick = function () {
                SVG.removeAttribute('data-current-id');
                CONFIGURATION.velocityMode = false;
                onUiObjectClick(id);
            }

            // Velocity
            velocity = document.createElementNS(SVG_NS, 'line');
            velocity.setAttribute('data-particle-id', id);
            velocity.setAttribute('class', 'velocity');
            SVG.appendChild(velocity);


            // Acceleratoion
            acceleration = document.createElementNS(SVG_NS, 'line');
            acceleration.setAttribute('data-particle-id', id);
            acceleration.setAttribute('class', 'acceleration');
            SVG.appendChild(acceleration);


            UI_ELEMENTS[id] = {
                ellipse: ellipse,
                velocity: velocity,
                acceleration: acceleration
            }

            // Add particle to the list
            let name = ("00" + id).slice(-2);
            $('#object-controls').append('<li data-particle-id="' + id + '">Particle ' + name + '</li>');
            $('#object-controls li[data-particle-id=' + id + ']')
                .off('click')
                .click(() => onUiObjectClick(id))
                .css('border-left-color', color);
        } else {
            ellipse = particleUI.ellipse;
            velocity = particleUI.velocity;
            acceleration = particleUI.acceleration;
        }

        let centerX = particle.vectors.position.x;
        let centerY = particle.vectors.position.y;

        // Draw the center
        ellipse.setAttribute('cx', centerX);
        ellipse.setAttribute('cy', centerY);
        ellipse.setAttribute('rx', radius);
        ellipse.setAttribute('ry', radius);
        ellipse.setAttribute('fill', color);


        // Draw velocity vector
        const vx = particle.vectors.velocity.x;
        const vy = particle.vectors.velocity.y;
        const vAbs = Math.sqrt(vx * vx + vy * vy);
        const dvx = vAbs === 0 ? 0 : radius * vx / vAbs;
        const dvy = vAbs === 0 ? 0 : radius * vy / vAbs;

        velocity.setAttribute('x1', centerX + dvx);
        velocity.setAttribute('y1', centerY + dvy);
        velocity.setAttribute('x2', centerX + dvx + _velocity2vector(vx));
        velocity.setAttribute('y2', centerY + dvy + _velocity2vector(vy));
        velocity.setAttribute('marker-end', vAbs > 0 ? 'url(#arrowhead)' : '')

        // Draw acceleration vector
        const ax = particle.vectors.acceleration.x;
        const ay = particle.vectors.acceleration.y;
        const aAbs = Math.sqrt(ax * ax + ay * ay);
        const dax = aAbs === 0 ? 0 : radius * ax / aAbs;
        const day = aAbs === 0 ? 0 : radius * ay / aAbs;

        acceleration.setAttribute('x1', centerX + dax);
        acceleration.setAttribute('y1', centerY + day);
        acceleration.setAttribute('x2', centerX + dax + ax * 0.01);
        acceleration.setAttribute('y2', centerY + day + ay * 0.01);
        acceleration.setAttribute('marker-end', aAbs > 0 ? 'url(#arrowhead)' : '')


        // Draw status
        if (id === CONFIGURATION.viewerParticleId) {
            $('#state').text(_state2string(particle));
        }
    }

    function _state2string(particle) {
        const state = {};
        // As the SVG's Y axiss is top-down, we invert both velocity and acceleration values
        state.mass = particle.mass.toExponential(3);
        state.position = {
            x: particle.vectors.position.x.toExponential(3),
            y: (2000 - 1.0 * particle.vectors.position.y).toExponential(3)
        };
        state.velocity = {
            x: particle.vectors.velocity.x.toExponential(3),
            y: (-1.0 * particle.vectors.velocity.y).toExponential(3)
        };
        state.acceleration = {
            x: particle.vectors.acceleration.x.toExponential(3),
            y: (-1.0 * particle.vectors.acceleration.y).toExponential(3)
        };

        return JSON.stringify(state, null, 2);
    }

    function onUiObjectClick(id) {
        const editor = $('#object-viewer');
        let currentEditorId = CONFIGURATION.viewerParticleId;
        editor.find('h6').text('Particle ' + id);
        let massInputValue = _mass2int(CONFIGURATION.particles[id].mass);
        $('#mass').val(massInputValue);
        if (id === currentEditorId) {
            // Hide the editor
            editor.toggleClass('invisible');
        } else {
            // Init the editor
            CONFIGURATION.viewerParticleId = id;
            editor.removeClass('invisible');
        }
    }

    function updateView() {
        // Update zoom controls
        //$('#gravity').val(_gravity2int(CONFIGURATION.gravity));
        //$('#gravity-value').text(CONFIGURATION.gravity.toExponential(1));


        /*
                // Clear current grid
                while (grid.lastChild) {
                    grid.removeChild(grid.lastChild);
                }
                if (CONFIGURATION.view.zoom < 0.4) return;

                const step = 100;
                let minX = vbX - step;
                let maxX = vbX + step + width;
                let minY = vbY - step;
                let maxY = vbY + step + height;

                // Round 'em up
                minX = Math.floor(minX / step) * step;
                maxX = Math.floor(maxX / step) * step;
                minY = Math.floor(minY / step) * step;
                maxY = Math.floor(maxY / step) * step;


                // Draw new grid
                for (let i = minX; i <= maxX; i = i + step) {
                    let line = document.createElementNS(SVG_NS, 'line');
                    line.setAttribute('x1', i);
                    line.setAttribute('x2', i);
                    line.setAttribute('y1', minY);
                    line.setAttribute('y2', maxY);
                    grid.appendChild(line);
                }
                for (let i = minY; i <= maxY; i = i + step) {
                    let line = document.createElementNS(SVG_NS, 'line');
                    line.setAttribute('x1', minX);
                    line.setAttribute('x2', maxX);
                    line.setAttribute('y1', i);
                    line.setAttribute('y2', i);
                    grid.appendChild(line);
                }
        */
    }

    /**
     *
     * @param {number} numColor
     * @returns {string}
     */
    function toStringColor(numColor) {
        return '#' + numColor.toString(16);
    }

    function generateRandomColor() {
        let colorNum = Math.floor(Math.random() * 16777215);
        if (colorNum.toString(16).length !== 6) {
            colorNum = generateRandomColor();
        }
        return colorNum;
    }

    function onUiGravityChange() {
        let i = Number.parseInt($('#gravity').val(), 10);
        const gravity = _int2gravity(i);
        $('#gravity-value').text(gravity.toExponential(1));
        CONFIGURATION.gravity = gravity;

        if (CONFIGURATION.mode === MODE_RUNNING) {
            SOCKET.send(JSON.stringify({
                type: 'GRAVITY_CONSTANT',
                text: CONFIGURATION.gravity
            }));
        }

    }

    function _int2gravity(i) {
        return i === 0 ? 0 : 20 * Math.pow(10, i / 20.0);
    }

    function _gravity2int(g) {
        return g === 0 ? 0 : Math.round(20 * Math.log10(g / 20.0));

    }

    function _createEditor(element, mode) {
        return ace.edit(element, {
            mode: mode,
            maxLines: 30,
            wrap: true,
            autoScrollEditorIntoView: true,
            showPrintMargin: false,
            vScrollBarAlwaysVisible: true,
            minLines: 8,
            scrollPastEnd: 0.5,
            theme: 'ace/theme/xcode'
        })
    }

    function _createWebSocket() {
        let url = window.location.href.replace(/[^/]*$/, '') + 'ws/socket';
        url = url.replace('http', 'ws');
        const webSocket = new WebSocket(url);
        webSocket.onmessage = onMessage;
        webSocket.onerror = function () {
            ELEMENT_LOG.append('<li class="ERROR">Network error</li>');
        };

        webSocket.onclose = function (event) {
            ELEMENT_LOG.append('<li>Connection closed, reason: "' + event.reason + '". Please reload the page.</li>');
        };

        // Optional. Some browsers (or networks) may not support ping/pong messaging,
        // so we make sure our socket keeps the connection alive
        setInterval(function () {
            if (webSocket.readyState === webSocket.OPEN) {
                let ping = {
                    type: 'PING',
                    payload: 'I am alive!'
                };

                webSocket.send(JSON.stringify(ping));
            }
        }, 19391);
        return webSocket;
    }

    return {

        reset: function () {
            _sendStop();
            //$('#gravity').val(DEFAULT_GRAVITY);
            //updateView();
        },

        start: function () {
            SOCKET.send(JSON.stringify({
                type: 'START',
                text: CONFIGURATION.gravity
            }));
            //_sendStop();
            //$('#gravity').val(DEFAULT_GRAVITY);
            //updateView();
        },

        onUiGravityChange: function () {
            let i = Number.parseInt($('#gravity').val(), 10);
            const gravity = _int2gravity(i);
            $('#gravity-value').text(gravity.toExponential(1));
            CONFIGURATION.gravity = gravity;

            if (CONFIGURATION.mode === MODE_RUNNING) {
                SOCKET.send(JSON.stringify({
                    type: 'GRAVITY_CONSTANT',
                    text: CONFIGURATION.gravity
                }));
            }
        }
    }
})();
