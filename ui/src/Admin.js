import React, {Component} from 'react'
import {Button, Icon, Input, List, Modal, Table} from 'semantic-ui-react'

export default class Admin extends Component {

    state = {
        creating: false,
        pendingDeletions: []
    };

    renderEditRow = () => {
        if (this.state.creating) {
            return (
                <Table.Row key='creator'>
                    <Table.Cell><Input fluid placeholder='Name'/></Table.Cell>
                    <Table.Cell textAlign='center'>
                        <Button basic  icon>
                            <Icon name='plus circle'/>
                        </Button>
                    </Table.Cell>
                    <Table.Cell><Input fluid placeholder='Pattern'/></Table.Cell>
                    <Table.Cell><Input fluid className='code-input' placeholder='URL'/></Table.Cell>
                    <Table.Cell textAlign='center'>
                        <Button
                            basic
                            positive
                            circular
                            icon
                            onClick={() => this.props.onCreate({})}
                        >
                            <Icon name='save' size='large'/>
                        </Button>
                    </Table.Cell>
                </Table.Row>
            )
        }
    };

    handleModalClose = () => {
        this.setState({
            creating: false
        });
    };

    dispatchDelete = (sourceId) => {
        const pendingDeletions = this.state.pendingDeletions.slice();
        pendingDeletions.push(sourceId);
        this.setState({pendingDeletions: pendingDeletions});
        this.props.onDelete(sourceId, (success) => {
            this.setState({pendingDeletions: this.state.pendingDeletions.filter(a => a !== sourceId)});
        });
    };

    render() {
        const {creating, pendingDeletions} = this.state;
        const sources = this.props.sources;
        const editRow = this.renderEditRow();
        return (
            <Modal size='fullscreen' dimmer='inverted' onClose={this.handleModalClose}
                   trigger={<Button icon><Icon name='setting'/></Button>}>
                <Modal.Header>Manage Sources</Modal.Header>
                <Modal.Content image>
                    <Modal.Description>
                        <Table celled>
                            <Table.Header>
                                <Table.Row>
                                    <Table.HeaderCell textAlign='center' width='2'>Name</Table.HeaderCell>
                                    <Table.HeaderCell textAlign='center' width='1'>Data Types</Table.HeaderCell>
                                    <Table.HeaderCell textAlign='center' width='2'>Data Pattern</Table.HeaderCell>
                                    <Table.HeaderCell textAlign='center' width='1'>URL</Table.HeaderCell>
                                    <Table.HeaderCell width='1'/>
                                </Table.Row>
                            </Table.Header>
                            <Table.Body>
                                {sources.map(source => {
                                    return (
                                        <Table.Row key={source.id}>
                                            <Table.Cell textAlign='center'>{source.name}</Table.Cell>
                                            <Table.Cell>
                                                <List ordered>
                                                    {source.dataLabels.map(dataLabel => (
                                                        <List.Item key={source.id + "_" + dataLabel}>
                                                            {dataLabel}
                                                        </List.Item>
                                                    ))}
                                                </List>
                                            </Table.Cell>
                                            <Table.Cell textAlign='center'><code>{source.pattern}</code></Table.Cell>
                                            <Table.Cell textAlign='center'><code>{source.url}</code></Table.Cell>
                                            <Table.Cell textAlign='center'>
                                                <Button
                                                    loading={pendingDeletions.includes(source.id)}
                                                    disabled={pendingDeletions.includes(source.id)}
                                                    basic
                                                    negative
                                                    circular
                                                    icon
                                                    onClick={() => this.dispatchDelete(source.id)}
                                                >
                                                    <Icon name='trash alternate' size='large'/>
                                                </Button>
                                            </Table.Cell>
                                        </Table.Row>
                                    );
                                })}

                                {editRow}

                            </Table.Body>
                            <Table.Footer fullWidth>
                                <Table.Row>
                                    <Table.HeaderCell colSpan='6'>
                                        {
                                            creating ?
                                                <Button
                                                    floated='right'
                                                    icon
                                                    labelPosition='left'
                                                    primary
                                                    size='small'
                                                    onClick={() => this.setState({creating: false})}
                                                >
                                                    <Icon name='times'/> Cancel
                                                </Button>
                                                :
                                                <Button
                                                    floated='right'
                                                    icon
                                                    labelPosition='left'
                                                    primary
                                                    size='small'
                                                    onClick={() => this.setState({creating: true})}
                                                >
                                                    <Icon name='plus'/> Add Source
                                                </Button>}
                                    </Table.HeaderCell>
                                </Table.Row>
                            </Table.Footer>
                        </Table>
                    </Modal.Description>
                </Modal.Content>
            </Modal>
        );
    }
}
