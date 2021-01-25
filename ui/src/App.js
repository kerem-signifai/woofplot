import util from './util'
import _ from 'lodash';

import React, {Component} from 'react';
import {Button, Checkbox, Dropdown, Form, Grid, Header, Icon, Input, Label, Loader, Message, Modal, Popup} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';

import Admin from './Admin.js'

import 'chartjs-plugin-colorschemes';
import './style.css';
import RangePicker from "./RangePicker";

const api = '/api/';
const REFRESH_MS = 30_000;

export default class App extends Component {
    chartRef = React.createRef();

    selectedSettings = {
        sourcesLeft: [],
        sourcesRight: [],
        rawElements: '',
        autoElements: true,
        relativeRange: null,
        absoluteRange: null
    };

    state = {
        userLoading: true,
        user: null,
        loginOpen: false,
        loginFailed: false,
        loginLoading: false,
        username: null,
        password: null,
        userDropdownOpen: false,

        changePasswordOpen: false,
        changePasswordFailed: false,
        changePasswordLoading: false,
        newPassword: null,
        newPasswordValidate: null,

        prePlotError: null,
        isFetching: false,
        isLoading: false,
        loadedSources: [],
        loadedSeries: [],
        selectedSettings: this.selectedSettings,
        plottedSettings: null,
        displayedSettings: null,
        datasets: []
    };
    leftAxisRef = React.createRef();
    rightAxisRef = React.createRef();

    limitInputRef = React.createRef();
    plotButton = React.createRef();

    handleErrors = (response) => {
        if (!response.ok) throw new Error(response.statusText);
        return response;
    };

    componentWillUnmount = () => {
        clearInterval(this.interval);
    };

    componentDidMount = () => {
        this.interval = setInterval(() => {
            this.loadData(true);
            this.fetchSources(true);
        }, REFRESH_MS);

        this.fetchUser();

        const chartContainer = document.getElementById('chart-wrapper');
        const dataCanvas = document.getElementById('data-chart');
        const cursorCanvas = document.getElementById('cursor');
        const cursorCtx = cursorCanvas.getContext('2d');

        chartContainer.onmousemove = (e) => {
            const chartArea = this.chartRef.current.chartInstance.chartArea;
            const dataWidth = parseInt(dataCanvas.style.width, 10);
            const dataHeight = parseInt(dataCanvas.style.height, 10);
            cursorCanvas.width = dataWidth;
            cursorCanvas.height = dataHeight;
            const {offsetLeft, offsetTop} = cursorCanvas;
            const {clientX, clientY} = e;
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
            if (clientX <= offsetLeft + dataWidth && clientX >= 48 && clientY <= offsetTop + dataHeight && clientY >= offsetTop) {
                cursorCtx.beginPath();
                cursorCtx.moveTo(clientX - offsetLeft, chartArea.top);
                cursorCtx.lineTo(clientX - offsetLeft, chartArea.bottom);
                cursorCtx.stroke();
            }
        };
        chartContainer.onmouseleave = () => {
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
        };

        this.fetchSources(false);
    };

    fetchUser = () => {
        fetch(`${api}user`)
            .then(this.handleErrors)
            .then(response => response.json())
            .then(result => {
                this.setState({
                    user: result
                })
            })
            .catch(() => {})
            .finally(() => {
                this.setState({
                    userLoading: false
                })
            });
    }

    createSource = (data, callback) => {
        fetch(`${api}source`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(data)
        })
            .then(this.handleErrors)
            .then(() => this.fetchSources(true).then(() => callback(true)))
            .catch(() => this.fetchSources(true).then(() => callback(false)));
    };

    deleteSource = (source, callback) =>
        fetch(`${api}source/${encodeURIComponent(source)}`, {
            method: 'delete'
        })
            .then(this.handleErrors)
            .then(() => this.fetchSources(true).then(() => callback(true)))
            .catch(() => this.fetchSources(true).then(() => callback(false)));

    peekSource = (source, callback) =>
        fetch(`${api}peek/${encodeURIComponent(source)}`)
            .then(this.handleErrors)
            .then(response => response.json())
            .then(result => callback(true, result))
            .catch((e) => callback(false, e.message));

    syncSource = (source, history, callback) =>
        fetch(`${api}sync/${encodeURIComponent(source)}?history=${history}`, {
            method: 'POST'
        })
            .then(this.handleErrors)
            .then(result => callback(true, null))
            .catch((e) => callback(false, e.message));

    fetchSources = (background) => {

        if (!background) {
            this.setState({isFetching: true});
        }
        return fetch(`${api}source`)
            .then(this.handleErrors)
            .then(response => response.json())
            .then(results => {
                const loaded = results.flatMap(source =>
                    source.fields.map(field => ({
                        source: source,
                        datalabel: field.label
                    }))
                );
                const sources = Object.assign({}, ...loaded.map(entry => {
                    const source = entry.source;
                    const datalabel = entry.datalabel;
                    const id = source.url + ':' + datalabel;
                    return {
                        [id]: {
                            selected: false,
                            key: id,
                            datalabel: datalabel,
                            value: id,
                            text: source.name + ' [' + datalabel + ']',
                            source: source
                        }
                    }
                }));
                const {selectedSettings, plottedSettings} = this.state;

                const selectedLeft = selectedSettings.sourcesLeft.filter(source => source in sources);
                const selectedRight = selectedSettings.sourcesRight.filter(source => source in sources);

                let currentPlotted = plottedSettings;
                if (currentPlotted !== null) {
                    const plottedLeft = currentPlotted.sourcesLeft.filter(source => source in sources);
                    const plottedRight = currentPlotted.sourcesRight.filter(source => source in sources);
                    currentPlotted = {...currentPlotted, sourcesLeft: plottedLeft, sourcesRight: plottedRight}
                }

                this.leftAxisRef.current.setState({value: selectedLeft});
                this.rightAxisRef.current.setState({value: selectedRight});

                this.setState({
                    loadedSources: results,
                    loadedSeries: sources,
                    selectedSettings: {...selectedSettings, sourcesLeft: selectedLeft, sourcesRight: selectedRight},
                    plottedSettings: currentPlotted
                });
                if (!background) {
                    this.setState({isFetching: false});
                }
            })
            .catch((reason) => {
                console.log(reason);
                if (!background) {
                    this.setState({isFetching: false});
                }
            });
    };

    handlePlot = () => this.setState({plottedSettings: this.state.selectedSettings}, () => this.loadData(false));

    loadData = (background) => {
        const {plottedSettings, loadedSeries, prePlotError} = this.state;

        if (plottedSettings) {
            const {sourcesLeft, sourcesRight, relativeRange, absoluteRange, autoElements, rawElements} = plottedSettings;

            if (prePlotError == null) {
                let timeRange;
                let rawParam;
                let queryInterval;

                if (relativeRange) {
                    const retentionMinutes = relativeRange.value;
                    timeRange = (retentionMinutes === -1 && rawElements) ?
                        '' :
                        `&from=${Date.now() - (retentionMinutes === -1 ? 60 : retentionMinutes) * 60 * 1000}&to=${Date.now()}`;
                    queryInterval = autoElements ? (retentionMinutes === -1 ? 'minute' : relativeRange.interval) : 'moment';
                } else {
                    const {start, end, interval} = absoluteRange;
                    timeRange = `&from=${start.getTime()}&to=${end.getTime()}`
                    queryInterval = autoElements ? interval : 'moment';
                }

                rawParam = rawElements ? `&raw_elements=${rawElements}` : '';
                if (!background) {
                    this.setState({isLoading: true, hasPlotted: true});
                }
                const agg = autoElements ? 'average' : 'raw';
                Promise.all(
                    sourcesLeft.concat(sourcesRight).map(sourceId =>
                        fetch(`${api}query?source=${encodeURIComponent(sourceId)}${timeRange}&aggregation=${agg}&interval=${queryInterval}${rawParam}`)
                            .then(this.handleErrors)
                            .then(response => response.json())
                            .then(results => {
                                const sourceRef = loadedSeries[sourceId];
                                return {
                                    label: sourceRef.text,
                                    yAxisID: sourcesRight.includes(sourceRef.key) ? 'y-axis-r' : 'y-axis-l',
                                    source: sourceRef,
                                    fill: false,
                                    borderJoinStyle: 'round',
                                    lineTension: 0,
                                    pointRadius: 3,
                                    pointHoverRadius: 6,
                                    data: results.map(source => {
                                        return {
                                            x: source.timestamp,
                                            y: source.value
                                        };
                                    })
                                };
                            })
                    )
                ).then(results => {
                    if (!background) {
                        this.setState({isLoading: false});
                    }
                    if (!background || _.isEqual(this.state.plottedSettings, plottedSettings)) {
                        this.setState({
                            datasets: results,
                            displayedSettings: plottedSettings
                        });
                    }
                }).catch((reason) => {
                    console.log(reason);
                });
            }
        }
    };

    handleAutoElementToggle = (event, data) => {
        if (!data.checked) {
            this.setState({selectedSettings: {...this.state.selectedSettings, autoElements: false}});
            setTimeout(() => {
                this.limitInputRef.current.focus();
            }, 0)
        } else {
            this.setState({selectedSettings: {...this.state.selectedSettings, rawElements: '', autoElements: true}});
        }
    };

    handleElementInput = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, rawElements: event.target.value}});
    };

    selectRelativeRange = (relativeRange) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, relativeRange: relativeRange, absoluteRange: null}})
    }

    selectAbsoluteRange = (absoluteRange) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, relativeRange: null, absoluteRange: absoluteRange}})
    }

    handleLeftSourceSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, sourcesLeft: data.value}});
    };

    handleRightSourceSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, sourcesRight: data.value}});
    };

    isPlottable = () => {
        const {selectedSettings} = this.state;
        return (selectedSettings.autoElements || (selectedSettings.rawElements && util.isInt(selectedSettings.rawElements)));
    };

    setCredentials = (e, {name, value}) => {
        this.setState({[name]: value});
    }

    submitLogout = () => {
        this.setState({userLoading: true});
        fetch(`${api}logout`, {
            method: 'POST'
        })
            .then(this.handleErrors)
            .then(() => this.setState({user: null, userDropdownOpen: false}))
            .finally(() => this.setState({userLoading: false}));
    }

    newPasswordValid = () => {
        const {newPassword, newPasswordValidate} = this.state;
        return newPassword && newPasswordValidate && newPassword === newPasswordValidate;
    }

    changePassword = () => {
        const {user, newPassword} = this.state;
        this.setState({changePasswordLoading: true});
        fetch(`${api}changepassword`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({
                username: user.username,
                password: newPassword
            })
        })
            .then(this.handleErrors)
            .then(() => this.setState({changePasswordOpen: false, changePasswordFailed: false, newPassword: null, newPasswordValidate: null}))
            .catch(() => this.setState({changePasswordFailed: true}))
            .finally(() => this.setState({changePasswordLoading: false}));
    }

    submitLogin = () => {
        const {username, password} = this.state;
        this.setState({loginLoading: true});
        fetch(`${api}login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({
                username: username,
                password: password
            })
        })
            .then(this.handleErrors)
            .then(response => response.json())
            .then((result) => this.setState({user: result, loginOpen: false, loginFailed: false}))
            .catch(() => this.setState({loginFailed: true}))
            .finally(() => this.setState({loginLoading: false}));
    };

    render() {
        const {
            selectedSettings, displayedSettings,
            isFetching, isLoading,
            loadedSeries, loadedSources,
            datasets, prePlotError,
            loginFailed, loginOpen, username, password, loginLoading, user, userLoading, userDropdownOpen,
            changePasswordOpen, changePasswordFailed, changePasswordLoading, newPasswordValidate
        } = this.state;
        const {relativeRange, absoluteRange, sourcesLeft, sourcesRight, autoElements} = displayedSettings || {};

        let minDate;
        let maxDate;
        let rangeUnit;
        let format;
        if (relativeRange) {
            if (relativeRange.value === -1 && !autoElements) {
                minDate = null;
            } else {
                minDate = Date.now() - (relativeRange.value === -1 ? 60 : relativeRange.value) * 60 * 1000;
            }
            maxDate = Date.now();
            rangeUnit = relativeRange.unit;
            format = relativeRange.format;
        } else if (absoluteRange) {
            minDate = absoluteRange.start;
            maxDate = absoluteRange.end;
            rangeUnit = absoluteRange.unit;
            format = absoluteRange.format;
        }

        const minPoint = Math.min(datasets.map(set => set.data ? set.data.length > 0 ? set.data[0].x ? set.data[0].x : 0 : 0 : 0))
        const maxPoint = Math.max(datasets.map(set => set.data ? set.data.length > 0 ? set.data[set.data.length - 1].x ? set.data[set.data.length - 1].x : 0 : 0 : 0))
        const maxDataWidth = maxPoint - minPoint;

        const unit = rangeUnit
            ? rangeUnit
            : maxDataWidth >= 1.5 * 30 * 24 * 60 * 60 * 1000 ? 'month'
                : maxDataWidth >= 10 * 24 * 60 * 60 * 1000 ? 'week'
                    : maxDataWidth >= 24 * 60 * 60 * 1000 ? 'day'
                        : maxDataWidth >= 2 * 60 * 60 * 1000 ? 'hour'
                            : 'minute';
        const timeDisplayFormats = {}
        if (format) {
            timeDisplayFormats[unit] = format;
        }
        const options = {
            animation: {
                duration: 0
            },
            plugins: {
                colorschemes: {
                    scheme: 'office.Berlin6'
                }
            },
            responsive: true,
            tooltips: {
                intersect: false,
                axis: 'x'
            },
            hover: {
                mode: 'nearest',
                intersect: false,
                axis: 'x',
                animationDuration: 100
            },
            scales: {
                yAxes: [
                    {
                        id: 'y-axis-l',
                        position: 'left',
                        gridLines: {
                            display: sourcesLeft && sourcesLeft.length
                        },
                        ticks: {
                            display: sourcesLeft && sourcesLeft.length
                        }
                    },
                    {
                        id: 'y-axis-r',
                        position: 'right',
                        gridLines: {
                            display: sourcesRight && sourcesRight.length
                        },
                        ticks: {
                            display: sourcesRight && sourcesRight.length
                        }
                    }
                ],
                xAxes: [
                    {
                        type: 'time',
                        bounds: 'ticks',
                        time: {
                            displayFormats: timeDisplayFormats,
                            unit: unit
                        },
                        ticks: {
                            maxTicksLimit: maxDate ? 12 : -1,
                            autoSkip: true,
                            maxRotation: 0,
                            minRotation: 0,
                            min: minDate,
                            max: maxDate
                        },
                        distribution: 'linear'
                    }
                ]
            }
        };

        return (
            <div className='container'>
                <span className='datasource-select'>
                    <Dropdown
                        ref={this.leftAxisRef}
                        className='left-datasource-dropdown'
                        placeholder='Left axis data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(loadedSeries).filter(src => !selectedSettings.sourcesRight.includes(src.key))}
                        onChange={this.handleLeftSourceSelect}
                    />
                    <Dropdown
                        ref={this.rightAxisRef}
                        className='right-datasource-dropdown'
                        placeholder='Right axis data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(loadedSeries).filter(src => !selectedSettings.sourcesLeft.includes(src.key))}
                        onChange={this.handleRightSourceSelect}
                    />
                </span>
                <span className='element-limit-select'>
                    <Popup
                        position='top center'
                        trigger={<Header as='h4' textAlign='center' className='helpable-text'>Element count</Header>}
                    >
                        The number of elements to query. If set to <strong>Auto</strong>, elements will be limited to the provided <strong>Time period</strong> and aggregated & displayed automatically.
                        If not, the number of elements to query must be explicitly provided.
                    </Popup>
                    <Grid className='element-limit'>
                    <Checkbox
                        label='Auto'
                        onChange={this.handleAutoElementToggle}
                        className='element-limit-auto'
                        checked={selectedSettings.autoElements}
                    />
                    <Input
                        ref={this.limitInputRef}
                        className='element-limit-input'
                        fluid
                        onChange={this.handleElementInput}
                        value={selectedSettings.rawElements}
                        error={!!selectedSettings.rawElements && !util.isInt(selectedSettings.rawElements)}
                        disabled={selectedSettings.autoElements}
                        placeholder='Limit'
                        onKeyPress={e => {
                            if (e.charCode === 13) {
                                this.plotButton.current.handleClick(e);
                            }
                        }}
                    />
                    </Grid>
                </span>
                <RangePicker defaultRange={60} onRelative={this.selectRelativeRange} onAbsolute={this.selectAbsoluteRange} loading={isLoading}/>

                <span className='admin-launcher'>
                    <span className='account-info'>
                        {userLoading ?
                            <span className='login-loader'>
                                    <Loader className='login-loader' active inline='centered' size='tiny'/>
                                </span>
                            :
                            user ?
                                <Dropdown icon={null} open={userDropdownOpen} onClose={() => this.setState({userDropdownOpen: false})} className='account-dropdown' trigger={
                                    <span className='user-button' onClick={() => this.setState({userDropdownOpen: true})}>
                                        <Icon name='user'/>
                                        <span className='sign-in-text'>Hello, <strong>{user.username}</strong></span>
                                        <Icon className='range-dropdown-icon' name='angle down'/>
                                    </span>
                                }>
                                    <Dropdown.Menu>
                                        <Dropdown.Item disabled text={<span>Signed in as <strong>{user.username}</strong></span>}/>
                                        <Dropdown.Item icon='edit' text='Change password' onClick={() => this.setState({changePasswordOpen: true})}/>
                                        <Dropdown.Item icon='power' text='Sign out' onClick={this.submitLogout}/>
                                        <Modal
                                            dimmer='inverted'
                                            centered={false}
                                            size='mini'
                                            open={changePasswordOpen}
                                            onClose={() => this.setState({changePasswordOpen: false, changePasswordFailed: false, newPassword: null, newPasswordValidate: null})}>
                                            <Modal.Content>
                                                <Grid centered padded='vertically'>
                                                    <Form loading={changePasswordLoading} onSubmit={this.changePassword} error={changePasswordFailed}>
                                                        <Form.Input style={{display: 'none'}} autoComplete='username'/>
                                                        <Form.Input
                                                            label='New password'
                                                            autoFocus
                                                            onChange={this.setCredentials}
                                                            name='newPassword'
                                                            autoComplete='new-password'
                                                            type='password'
                                                            placeholder='Password'
                                                        />
                                                        <Form.Input
                                                            label='Repeat new password'
                                                            onChange={this.setCredentials}
                                                            name='newPasswordValidate'
                                                            autoComplete='new-password'
                                                            type='password'
                                                            error={(!this.newPasswordValid() && newPasswordValidate && newPasswordValidate.length > 0) ?
                                                                <Label className='flexible-label' pointing>Passwords must match</Label>
                                                            :
                                                            null}
                                                            placeholder='Password'
                                                        />
                                                        <Form.Field disabled={!this.newPasswordValid()} control={Button}>Change password</Form.Field>
                                                        {changePasswordFailed ? <Message attached='bottom' compact error>Operation failed</Message> : null}
                                                    </Form>
                                                </Grid>
                                            </Modal.Content>
                                        </Modal>
                                    </Dropdown.Menu>
                                </Dropdown>
                                :
                                <Modal
                                    dimmer='inverted'
                                    centered={false}
                                    size='mini'
                                    open={loginOpen}
                                    onClose={() => this.setState({loginOpen: false, loginFailed: false, username: null, password: null})}
                                    trigger={
                                        <span onClick={() => this.setState({loginOpen: true})} className='login-button'>
                                    <Icon name='user'/><span className='sign-in-text'>Sign in</span>
                                </span>
                                    }>
                                    <Modal.Content>
                                        <Grid centered padded='vertically'>
                                            <Form loading={loginLoading} onSubmit={this.submitLogin} error={loginFailed}>
                                                <Form.Field>
                                                    <Input onChange={this.setCredentials} name='username' autoFocus autoComplete='current-username' icon='user' iconPosition='left'
                                                           placeholder='Username'/>
                                                </Form.Field>
                                                <Form.Field>
                                                    <Input onChange={this.setCredentials} name='password' autoComplete='current-password' type='password' icon='lock' iconPosition='left'
                                                           placeholder='Password'/>
                                                </Form.Field>
                                                <Form.Field disabled={!username || !password} control={Button}>Sign in</Form.Field>
                                                {loginFailed ? <Message attached='bottom' compact error>Invalid credentials</Message> : null}
                                            </Form>
                                        </Grid>
                                    </Modal.Content>
                                </Modal>
                        }
                    </span>


                    <span className='menu-buttons'>
                    <Button
                        size='small'
                        ref={this.plotButton}
                        loading={isFetching || isLoading}
                        disabled={!this.isPlottable() || isFetching || isLoading || prePlotError != null}
                        onClick={this.handlePlot}
                    >
                        Plot
                    </Button>

                    <Admin sources={loadedSources} user={user}
                           peekSource={(source, callback) => this.peekSource(source, callback)}
                           onDelete={(source, callback) => this.deleteSource(source, callback)}
                           onCreate={(source, callback) => this.createSource(source, callback)}
                           onSync={(source, history, callback) => this.syncSource(source, history, callback)}
                    />
                    </span>
                </span>

                <br style={{clear: 'both'}}/>
                <div className='chart-container' id='chart-wrapper'>
                    <canvas id='cursor'/>
                    <Line ref={this.chartRef} id='data-chart' data={{datasets: datasets}} options={options} redraw={false}/>
                </div>
            </div>
        );
    }
}
